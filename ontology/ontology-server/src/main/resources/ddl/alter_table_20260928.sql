SET search_path TO ontology;

CREATE TABLE IF NOT EXISTS function_version
(
    id              BIGSERIAL PRIMARY KEY,
    function_id     BIGINT       NOT NULL,
    function_api    VARCHAR(255) NOT NULL,
    version_no      INT4         NOT NULL,
    code            TEXT,
    reference_name  VARCHAR(512),
    version_status  VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    change_log      VARCHAR(1024),
    create_by       VARCHAR(128),
    publish_time    TIMESTAMP(6),
    create_time     TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_function_version_no UNIQUE (function_id, version_no)
);

CREATE INDEX IF NOT EXISTS idx_function_version_api ON function_version (function_api);
CREATE UNIQUE INDEX IF NOT EXISTS uk_function_draft_version
    ON function_version (function_id) WHERE version_status = 'DRAFT';

-- 多个已发布版本可以并存；旧实现中的 DEPRECATED 版本恢复为可绑定的 PUBLISHED 版本。
DROP INDEX IF EXISTS uk_function_published_version;
ALTER TABLE function_version DROP CONSTRAINT IF EXISTS ck_function_version_status;
UPDATE function_version SET version_status = 'PUBLISHED' WHERE version_status = 'DEPRECATED';
ALTER TABLE function_version
    ADD CONSTRAINT ck_function_version_status CHECK (version_status IN ('DRAFT', 'PUBLISHED'));

ALTER TABLE function ADD COLUMN IF NOT EXISTS latest_version_no INT4 NOT NULL DEFAULT 1;
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

-- 每个存量函数生成一个初始已发布版本。
INSERT INTO function_version
    (function_id, function_api, version_no, code, reference_name, version_status,
     change_log, create_time, update_time, publish_time)
SELECT f.id, f.api, 1, f.code, f.reference_name, 'PUBLISHED',
       '存量数据迁移生成初始版本', f.create_time, f.update_time, f.update_time
FROM function f
WHERE NOT EXISTS (SELECT 1 FROM function_version v WHERE v.function_id = f.id);

UPDATE function f
SET latest_version_no = versions.max_version_no
FROM (
    SELECT function_id, MAX(version_no) AS max_version_no
    FROM function_version
    GROUP BY function_id
) versions
WHERE versions.function_id = f.id
  AND f.latest_version_no < versions.max_version_no;

UPDATE function_param p
SET function_version_id = v.id
FROM function_version v
WHERE v.function_id = p.function_id
  AND v.version_no = 1
  AND p.function_version_id IS NULL;

UPDATE ontology_action a
SET function_version_id = v.id
FROM function f
JOIN function_version v ON v.function_id = f.id AND v.version_no = 1
WHERE f.api = a.function_api
  AND a.function_api IS NOT NULL
  AND a.function_version_id IS NULL;

UPDATE function_execute_result r
SET function_version_id = v.id
FROM function f
JOIN function_version v ON v.function_id = f.id AND v.version_no = 1
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
ALTER TABLE function_param DROP COLUMN IF EXISTS function_version_no;
ALTER TABLE ontology_action DROP COLUMN IF EXISTS function_version_no;
ALTER TABLE function_execute_result DROP COLUMN IF EXISTS function_version_no;
