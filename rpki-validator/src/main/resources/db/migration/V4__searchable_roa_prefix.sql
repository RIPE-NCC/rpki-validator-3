DELETE FROM rpki_object;

ALTER TABLE rpki_object_roa_prefixes ADD COLUMN prefix_family TINYINT NOT NULL;
ALTER TABLE rpki_object_roa_prefixes ADD COLUMN prefix_begin DECIMAL(39, 0) NOT NULL;
ALTER TABLE rpki_object_roa_prefixes ADD COLUMN prefix_end DECIMAL(39, 0) NOT NULL;
ALTER TABLE rpki_object_roa_prefixes ADD CONSTRAINT prefix_family_4_or_6 CHECK prefix_family IN (4, 6);

CREATE INDEX rpki_object_roa_prefixes__prefix_idx ON rpki_object_roa_prefixes (prefix_begin, prefix_end, prefix_family);
