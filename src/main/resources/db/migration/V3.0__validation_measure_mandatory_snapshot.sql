ALTER TABLE validation_measures
    ADD COLUMN mandatory_at_creation BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE validation_measures vm
SET    mandatory_at_creation = COALESCE(c.mandatory, FALSE)
FROM   poste_measure_catalog c
WHERE  vm.catalog_template_id = c.id;

CREATE INDEX idx_vm_validation_mandatory
    ON validation_measures (validation_id, mandatory_at_creation);
