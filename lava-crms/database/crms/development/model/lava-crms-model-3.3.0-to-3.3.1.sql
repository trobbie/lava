-- until the lava-crms V3.4.0 release is ready, all developers creating schema changes that will be
-- part of lava-crms V3.4.0 should update this script (i.e. the latest version of this script so
-- that you do not overwrite other developers changes)

DELIMITER ;

ALTER TABLE `patient` 
  ADD COLUMN `notes` VARCHAR(2000) NULL DEFAULT NULL AFTER `FullNameNoSuffix`;

