SET search_path TO ontology;
CREATE TABLE IF NOT EXISTS function_version (
 id BIGSERIAL PRIMARY KEY, function_id BIGINT NOT NULL, function_api VARCHAR(255) NOT NULL,
 version_no INT4 NOT NULL, code TEXT, reference_name VARCHAR(512), version_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
 change_log VARCHAR(1024), create_by VARCHAR(128), publish_time TIMESTAMP(6),
 create_time TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP, update_time TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_function_version_no UNIQUE(function_id,version_no),
 CONSTRAINT ck_function_version_status CHECK(version_status IN ('DRAFT','PUBLISHED','DEPRECATED'))
);
CREATE INDEX IF NOT EXISTS idx_function_version_api ON function_version(function_api);
CREATE UNIQUE INDEX IF NOT EXISTS uk_function_published_version ON function_version(function_id) WHERE version_status='PUBLISHED';
ALTER TABLE function ADD COLUMN IF NOT EXISTS latest_version_no INT4 NOT NULL DEFAULT 1;
ALTER TABLE function ADD COLUMN IF NOT EXISTS published_version_no INT4;
ALTER TABLE function ADD COLUMN IF NOT EXISTS published_version_id BIGINT;
ALTER TABLE function_param ADD COLUMN IF NOT EXISTS function_version_id BIGINT;
ALTER TABLE function_param ADD COLUMN IF NOT EXISTS function_version_no INT4;
ALTER TABLE ontology_action ADD COLUMN IF NOT EXISTS function_version_id BIGINT;
ALTER TABLE ontology_action ADD COLUMN IF NOT EXISTS function_version_no INT4;
ALTER TABLE function_execute_result ADD COLUMN IF NOT EXISTS function_version_id BIGINT;
ALTER TABLE function_execute_result ADD COLUMN IF NOT EXISTS function_version_no INT4;
CREATE INDEX IF NOT EXISTS idx_function_param_version ON function_param(function_version_id);
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM function_param p LEFT JOIN function f ON f.id=p.function_id WHERE f.id IS NULL) THEN RAISE EXCEPTION 'orphan function_param'; END IF;
 IF EXISTS(SELECT 1 FROM ontology_action a LEFT JOIN function f ON f.api=a.function_api WHERE a.function_api IS NOT NULL AND f.id IS NULL) THEN RAISE EXCEPTION 'action references missing function'; END IF;
END $$;
INSERT INTO function_version(function_id,function_api,version_no,code,reference_name,version_status,change_log,create_time,update_time,publish_time)
SELECT f.id,f.api,1,f.code,f.reference_name,'PUBLISHED','存量数据迁移生成初始版本',f.create_time,f.update_time,f.update_time FROM function f
WHERE NOT EXISTS(SELECT 1 FROM function_version v WHERE v.function_id=f.id);
UPDATE function f SET latest_version_no=GREATEST(f.latest_version_no,1),published_version_no=1,published_version_id=v.id FROM function_version v WHERE v.function_id=f.id AND v.version_no=1 AND f.published_version_id IS NULL;
UPDATE function_param p SET function_version_id=v.id,function_version_no=1 FROM function_version v WHERE v.function_id=p.function_id AND v.version_no=1 AND p.function_version_id IS NULL;
UPDATE ontology_action a SET function_version_id=f.published_version_id,function_version_no=f.published_version_no FROM function f WHERE f.api=a.function_api AND a.function_version_id IS NULL;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM function_param p JOIN function_version v ON v.id=p.function_version_id WHERE p.function_id<>v.function_id) THEN RAISE EXCEPTION 'parameter version mismatch'; END IF;
 IF EXISTS(SELECT 1 FROM function_version WHERE version_status='PUBLISHED' GROUP BY function_id HAVING count(*)>1) THEN RAISE EXCEPTION 'multiple published versions'; END IF;
 IF EXISTS(SELECT 1 FROM function f JOIN function_version v ON v.id=f.published_version_id WHERE f.id<>v.function_id OR f.published_version_no<>v.version_no) THEN RAISE EXCEPTION 'published pointer mismatch'; END IF;
 IF EXISTS(SELECT 1 FROM ontology_action a JOIN function_version v ON v.id=a.function_version_id WHERE a.function_api<>v.function_api) THEN RAISE EXCEPTION 'action version mismatch'; END IF;
END $$;
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conname='fk_function_version_function') THEN ALTER TABLE function_version ADD CONSTRAINT fk_function_version_function FOREIGN KEY(function_id) REFERENCES function(id) ON DELETE RESTRICT; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conname='fk_function_param_version') THEN ALTER TABLE function_param ADD CONSTRAINT fk_function_param_version FOREIGN KEY(function_version_id) REFERENCES function_version(id) ON DELETE RESTRICT; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conname='fk_function_published_version') THEN ALTER TABLE function ADD CONSTRAINT fk_function_published_version FOREIGN KEY(published_version_id) REFERENCES function_version(id) ON DELETE RESTRICT; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conname='fk_action_function_version') THEN ALTER TABLE ontology_action ADD CONSTRAINT fk_action_function_version FOREIGN KEY(function_version_id) REFERENCES function_version(id) ON DELETE RESTRICT; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conname='fk_execute_result_function_version') THEN ALTER TABLE function_execute_result ADD CONSTRAINT fk_execute_result_function_version FOREIGN KEY(function_version_id) REFERENCES function_version(id) ON DELETE RESTRICT; END IF;
END $$;
ALTER TABLE function_param ALTER COLUMN function_version_id SET NOT NULL;
ALTER TABLE function_param ALTER COLUMN function_version_no SET NOT NULL;
