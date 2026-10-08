SET search_path TO ontology;

CREATE TABLE IF NOT EXISTS function_version
(
    id              BIGSERIAL PRIMARY KEY,
    function_id     BIGINT       NOT NULL,
    function_api    VARCHAR(255) NOT NULL,
    version         VARCHAR(64)  NOT NULL,
    code            TEXT,
    reference_name  VARCHAR(512),
    version_status  VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    change_log      VARCHAR(1024),
    create_by       VARCHAR(128),
    publish_time    TIMESTAMP(6),
    create_time     TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_function_version_api ON function_version (function_api);

-- 兼容已落过整数 version_no 的中间版本：N 迁移为 N.0.0。
ALTER TABLE function_version ADD COLUMN IF NOT EXISTS version VARCHAR(64);
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'ontology'
          AND table_name = 'function_version'
          AND column_name = 'version_no'
    ) THEN
        EXECUTE 'UPDATE ontology.function_version
                 SET version = version_no::text || ''.0.0''
                 WHERE version IS NULL';
    END IF;
END $$;

-- 多个发布版本、多个草稿版本均可并存；旧 DEPRECATED 版本恢复为可绑定的 PUBLISHED。
DROP INDEX IF EXISTS uk_function_published_version;
DROP INDEX IF EXISTS uk_function_draft_version;
ALTER TABLE function_version DROP CONSTRAINT IF EXISTS ck_function_version_status;
ALTER TABLE function_version DROP CONSTRAINT IF EXISTS ck_function_version_format;
ALTER TABLE function_version DROP CONSTRAINT IF EXISTS uk_function_version_no;
ALTER TABLE function_version DROP CONSTRAINT IF EXISTS uk_function_version;
UPDATE function_version SET version_status = 'PUBLISHED' WHERE version_status = 'DEPRECATED';

ALTER TABLE function_param ADD COLUMN IF NOT EXISTS function_version_id BIGINT;
ALTER TABLE ontology_action ADD COLUMN IF NOT EXISTS function_version_id BIGINT;
ALTER TABLE function_execute_result ADD COLUMN IF NOT EXISTS function_version_id BIGINT;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM function_param p
        LEFT JOIN function f ON f.id = p.function_id
        WHERE f.id IS NULL
    ) THEN
        RAISE EXCEPTION 'orphan function_param';
    END IF;
    IF EXISTS (
        SELECT 1 FROM ontology_action a
        LEFT JOIN function f ON f.api = a.function_api
        WHERE a.function_api IS NOT NULL AND f.id IS NULL
    ) THEN
        RAISE EXCEPTION 'action references missing function';
    END IF;
END $$;

-- 每个尚未版本化的存量函数生成默认 1.0.0 已发布版本。
INSERT INTO function_version
    (function_id, function_api, version, code, reference_name, version_status,
     change_log, create_time, update_time, publish_time)
SELECT f.id, f.api, '1.0.0', f.code, f.reference_name, 'PUBLISHED',
       '存量数据迁移生成初始版本', f.create_time, f.update_time, f.update_time
FROM function f
WHERE NOT EXISTS (SELECT 1 FROM function_version v WHERE v.function_id = f.id);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM function_version WHERE version IS NULL) THEN
        RAISE EXCEPTION 'function_version.version backfill incomplete';
    END IF;
    IF EXISTS (
        SELECT 1 FROM function_version
        WHERE version !~ '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$'
    ) THEN
        RAISE EXCEPTION 'invalid semantic function version';
    END IF;
    IF EXISTS (
        SELECT 1 FROM function_version
        GROUP BY function_id, version
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'duplicate function version';
    END IF;
END $$;

ALTER TABLE function_version ALTER COLUMN version SET NOT NULL;
ALTER TABLE function_version
    ADD CONSTRAINT uk_function_version UNIQUE (function_id, version);
ALTER TABLE function_version
    ADD CONSTRAINT ck_function_version_format
    CHECK (version ~ '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$');
ALTER TABLE function_version
    ADD CONSTRAINT ck_function_version_status CHECK (version_status IN ('DRAFT', 'PUBLISHED'));

UPDATE function_param p
SET function_version_id = v.id
FROM function_version v
WHERE v.function_id = p.function_id
  AND v.version = '1.0.0'
  AND p.function_version_id IS NULL;

UPDATE ontology_action a
SET function_version_id = v.id
FROM function f
JOIN function_version v ON v.function_id = f.id AND v.version = '1.0.0'
WHERE f.api = a.function_api
  AND a.function_api IS NOT NULL
  AND a.function_version_id IS NULL;

UPDATE function_execute_result r
SET function_version_id = v.id
FROM function f
JOIN function_version v ON v.function_id = f.id AND v.version = '1.0.0'
WHERE f.api = r.function_api
  AND r.function_version_id IS NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM function_param p
        JOIN function_version v ON v.id = p.function_version_id
        WHERE p.function_id <> v.function_id
    ) THEN
        RAISE EXCEPTION 'parameter version mismatch';
    END IF;
    IF EXISTS (
        SELECT 1 FROM ontology_action a
        JOIN function_version v ON v.id = a.function_version_id
        WHERE a.function_api <> v.function_api
    ) THEN
        RAISE EXCEPTION 'action version mismatch';
    END IF;
    IF EXISTS (
        SELECT 1 FROM function_execute_result r
        JOIN function_version v ON v.id = r.function_version_id
        WHERE r.function_api <> v.function_api
    ) THEN
        RAISE EXCEPTION 'execute result version mismatch';
    END IF;
    IF EXISTS (SELECT 1 FROM function_param WHERE function_version_id IS NULL) THEN
        RAISE EXCEPTION 'function_param.function_version_id backfill incomplete';
    END IF;
    IF EXISTS (
        SELECT 1 FROM ontology_action
        WHERE function_api IS NOT NULL AND function_version_id IS NULL
    ) THEN
        RAISE EXCEPTION 'ontology_action.function_version_id backfill incomplete';
    END IF;
    IF EXISTS (SELECT 1 FROM function_execute_result WHERE function_version_id IS NULL) THEN
        RAISE EXCEPTION 'function_execute_result.function_version_id backfill incomplete';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_function_version_function'
          AND conrelid = 'ontology.function_version'::regclass
    ) THEN
        ALTER TABLE function_version
            ADD CONSTRAINT fk_function_version_function
            FOREIGN KEY (function_id) REFERENCES function (id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_function_param_version'
          AND conrelid = 'ontology.function_param'::regclass
    ) THEN
        ALTER TABLE function_param
            ADD CONSTRAINT fk_function_param_version
            FOREIGN KEY (function_version_id) REFERENCES function_version (id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_action_function_version'
          AND conrelid = 'ontology.ontology_action'::regclass
    ) THEN
        ALTER TABLE ontology_action
            ADD CONSTRAINT fk_action_function_version
            FOREIGN KEY (function_version_id) REFERENCES function_version (id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_execute_result_function_version'
          AND conrelid = 'ontology.function_execute_result'::regclass
    ) THEN
        ALTER TABLE function_execute_result
            ADD CONSTRAINT fk_execute_result_function_version
            FOREIGN KEY (function_version_id) REFERENCES function_version (id) ON DELETE RESTRICT;
    END IF;
END $$;

ALTER TABLE function_param ALTER COLUMN function_version_id SET NOT NULL;
ALTER TABLE function_execute_result ALTER COLUMN function_version_id SET NOT NULL;

ALTER TABLE function DROP CONSTRAINT IF EXISTS fk_function_published_version;
ALTER TABLE function DROP COLUMN IF EXISTS published_version_no;
ALTER TABLE function DROP COLUMN IF EXISTS published_version_id;
ALTER TABLE function DROP COLUMN IF EXISTS latest_version_no;
ALTER TABLE function_param DROP COLUMN IF EXISTS function_version_no;
ALTER TABLE ontology_action DROP COLUMN IF EXISTS function_version_no;
ALTER TABLE function_execute_result DROP COLUMN IF EXISTS function_version_no;
ALTER TABLE function_version DROP COLUMN IF EXISTS version_no;
