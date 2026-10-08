SET search_path TO ontology;

-- 最终模型：function 每一行就是一个函数版本，function.id 即 functionVersionId。
-- 同时兼容两种升级来源：旧单表模型，以及已经创建 function_version 的中间模型。
DO $$
BEGIN
    IF to_regclass('ontology.function_version') IS NOT NULL THEN
        -- 保留 function_version.id，避免迁移后 functionVersionId 变化。
        ALTER TABLE function DROP CONSTRAINT IF EXISTS fk_function_published_version;
        ALTER TABLE function_version DROP CONSTRAINT IF EXISTS fk_function_version_function;
        ALTER TABLE function RENAME TO function_definition_legacy;
        ALTER TABLE function_version RENAME TO function;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'ontology' AND table_name = 'function' AND column_name = 'function_api'
        ) AND NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'ontology' AND table_name = 'function' AND column_name = 'api'
        ) THEN
            ALTER TABLE function RENAME COLUMN function_api TO api;
        END IF;

        ALTER TABLE function ADD COLUMN IF NOT EXISTS description VARCHAR(25500);
        ALTER TABLE function ADD COLUMN IF NOT EXISTS display_name VARCHAR(255);
        ALTER TABLE function ADD COLUMN IF NOT EXISTS status SMALLINT;
        ALTER TABLE function ADD COLUMN IF NOT EXISTS type VARCHAR(255);
        ALTER TABLE function ADD COLUMN IF NOT EXISTS model VARCHAR(255);
        ALTER TABLE function ADD COLUMN IF NOT EXISTS object_types VARCHAR(2550);
        ALTER TABLE function ADD COLUMN IF NOT EXISTS ontology_space_id INTEGER;

        UPDATE function v
        SET description = d.description,
            display_name = d.display_name,
            status = d.status,
            type = d.type,
            model = d.model,
            object_types = d.object_types,
            ontology_space_id = d.ontology_space_id
        FROM function_definition_legacy d
        WHERE v.function_id = d.id;

        ALTER TABLE function DROP COLUMN IF EXISTS function_id;
        DROP TABLE function_definition_legacy;
    ELSE
        ALTER TABLE function ADD COLUMN IF NOT EXISTS version VARCHAR(64);
        ALTER TABLE function ADD COLUMN IF NOT EXISTS version_status VARCHAR(32);
        ALTER TABLE function ADD COLUMN IF NOT EXISTS change_log VARCHAR(1024);
        ALTER TABLE function ADD COLUMN IF NOT EXISTS create_by VARCHAR(128);
        ALTER TABLE function ADD COLUMN IF NOT EXISTS publish_time TIMESTAMP(6);

        UPDATE function SET version = '1.0.0' WHERE version IS NULL;
        UPDATE function SET version_status = 'PUBLISHED' WHERE version_status IS NULL;
        UPDATE function SET publish_time = COALESCE(update_time, create_time, CURRENT_TIMESTAMP)
        WHERE publish_time IS NULL;
    END IF;
END $$;

-- 清理中间版本字段和约束，建立最终约束。
ALTER TABLE function ADD COLUMN IF NOT EXISTS version VARCHAR(64);
ALTER TABLE function ADD COLUMN IF NOT EXISTS version_status VARCHAR(32);
ALTER TABLE function ADD COLUMN IF NOT EXISTS change_log VARCHAR(1024);
ALTER TABLE function ADD COLUMN IF NOT EXISTS create_by VARCHAR(128);
ALTER TABLE function ADD COLUMN IF NOT EXISTS publish_time TIMESTAMP(6);
ALTER TABLE function ADD COLUMN IF NOT EXISTS status SMALLINT;

UPDATE function SET version = '1.0.0' WHERE version IS NULL;
UPDATE function SET version_status = 'PUBLISHED' WHERE version_status IS NULL OR version_status = 'DEPRECATED';
UPDATE function SET status = 1 WHERE status IS NULL;

ALTER TABLE function DROP CONSTRAINT IF EXISTS uk_function_api;
ALTER TABLE function DROP CONSTRAINT IF EXISTS uk_function_api_version;
ALTER TABLE function DROP CONSTRAINT IF EXISTS uk_function_version;
ALTER TABLE function DROP CONSTRAINT IF EXISTS uk_function_version_no;
ALTER TABLE function DROP CONSTRAINT IF EXISTS ck_function_version_format;
ALTER TABLE function DROP CONSTRAINT IF EXISTS ck_function_version_status;
ALTER TABLE function DROP COLUMN IF EXISTS version_no;
ALTER TABLE function DROP COLUMN IF EXISTS published_version_no;
ALTER TABLE function DROP COLUMN IF EXISTS published_version_id;
ALTER TABLE function DROP COLUMN IF EXISTS latest_version_no;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM function GROUP BY api, version HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'duplicate function api/version exists';
    END IF;
    IF EXISTS (
        SELECT 1 FROM function
        WHERE version !~ '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$'
    ) THEN
        RAISE EXCEPTION 'invalid function version format exists';
    END IF;
END $$;

ALTER TABLE function ALTER COLUMN version SET NOT NULL;
ALTER TABLE function ALTER COLUMN version_status SET DEFAULT 'PUBLISHED';
ALTER TABLE function ALTER COLUMN version_status SET NOT NULL;
ALTER TABLE function ALTER COLUMN status SET NOT NULL;
ALTER TABLE function ADD CONSTRAINT uk_function_api_version UNIQUE (api, version);
ALTER TABLE function ADD CONSTRAINT ck_function_version_format
    CHECK (version ~ '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$');
ALTER TABLE function ADD CONSTRAINT ck_function_version_status
    CHECK (version_status IN ('DRAFT', 'PUBLISHED'));

ALTER TABLE function_param ADD COLUMN IF NOT EXISTS function_version_id BIGINT;
ALTER TABLE ontology_action ADD COLUMN IF NOT EXISTS function_version_id BIGINT;
ALTER TABLE function_execute_result ADD COLUMN IF NOT EXISTS function_version_id BIGINT;

-- 旧单表结构下 function_param.function_id 已经是最终版本 ID。
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'ontology'
          AND table_name = 'function_param'
          AND column_name = 'function_id'
    ) THEN
        EXECUTE 'UPDATE ontology.function_param
                 SET function_version_id = function_id
                 WHERE function_version_id IS NULL';
    END IF;
END $$;

-- 旧行为和执行记录没有版本 ID 时绑定迁移后的唯一初始版本。
UPDATE ontology_action a
SET function_version_id = f.id
FROM function f
WHERE a.function_api = f.api
  AND f.version = '1.0.0'
  AND a.function_version_id IS NULL;

UPDATE function_execute_result r
SET function_version_id = f.id
FROM function f
WHERE r.function_api = f.api
  AND f.version = '1.0.0'
  AND r.function_version_id IS NULL;

DO $$
BEGIN
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

ALTER TABLE function_param DROP CONSTRAINT IF EXISTS fk_function_param_version;
ALTER TABLE ontology_action DROP CONSTRAINT IF EXISTS fk_action_function_version;
ALTER TABLE function_execute_result DROP CONSTRAINT IF EXISTS fk_execute_result_function_version;

ALTER TABLE function_param ADD CONSTRAINT fk_function_param_version
    FOREIGN KEY (function_version_id) REFERENCES function(id) ON DELETE RESTRICT;
ALTER TABLE ontology_action ADD CONSTRAINT fk_action_function_version
    FOREIGN KEY (function_version_id) REFERENCES function(id) ON DELETE RESTRICT;
ALTER TABLE function_execute_result ADD CONSTRAINT fk_execute_result_function_version
    FOREIGN KEY (function_version_id) REFERENCES function(id) ON DELETE RESTRICT;

ALTER TABLE function_param ALTER COLUMN function_version_id SET NOT NULL;
ALTER TABLE function_execute_result ALTER COLUMN function_version_id SET NOT NULL;
ALTER TABLE function_param DROP COLUMN IF EXISTS function_id;
ALTER TABLE function_param DROP COLUMN IF EXISTS function_version_no;
ALTER TABLE ontology_action DROP COLUMN IF EXISTS function_version_no;
ALTER TABLE function_execute_result DROP COLUMN IF EXISTS function_version_no;

CREATE INDEX IF NOT EXISTS idx_function_api_create_time
    ON function(api, create_time DESC, id DESC);
