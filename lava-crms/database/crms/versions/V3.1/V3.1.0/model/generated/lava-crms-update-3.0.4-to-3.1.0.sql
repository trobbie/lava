SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0;
SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0;
SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='TRADITIONAL';

ALTER TABLE `lava_crms`.`contactlog` 
ADD INDEX `contactLog__authfilter` (`PIDN` ASC, `ProjName` ASC, `LogID` ASC) ;

ALTER TABLE `lava_crms`.`enrollmentstatus` 
ADD INDEX `enrollmentstatus__authfilter` (`PIDN` ASC, `ProjName` ASC, `EnrollStatID` ASC) ;

ALTER TABLE `lava_crms`.`instrumenttracking` 
ADD INDEX `instrumenttracking__PIDN` (`PIDN` ASC) 
, ADD INDEX `instrumenttracking__authfilter` (`PIDN` ASC, `ProjName` ASC, `InstrID` ASC) 
, DROP INDEX `insttumenttracking__PIDN` ;

ALTER TABLE `lava_crms`.`patient` DROP COLUMN `AGE` ;

ALTER TABLE `lava_crms`.`visit` 
  ADD CONSTRAINT `visit__PIDN_PROJNAME`
  FOREIGN KEY (`PIDN` , `ProjName` )
  REFERENCES `lava_crms`.`enrollmentstatus` (`PIDN` , `ProjName` )
  ON DELETE NO ACTION
  ON UPDATE NO ACTION
, ADD INDEX `visit__PIDN_PROJNAME` (`PIDN` ASC, `ProjName` ASC) 
, ADD INDEX `visit__authfilter` (`PIDN` ASC, `ProjName` ASC, `VID` ASC) 
, ADD INDEX `visit__date` (`VID` ASC, `VDate` ASC, `VTime` ASC) ;

ALTER TABLE `lava_crms`.`patientconsent` DROP COLUMN `CTcarepart` , DROP COLUMN `CTpart` , DROP COLUMN `CTmusic` , DROP COLUMN `CTfollowup` , DROP COLUMN `CTotherstudy` , DROP COLUMN `CT4t` , DROP COLUMN `CT1point5T` , DROP COLUMN `CTmediaedu` , DROP COLUMN `CTaudio` , DROP COLUMN `CTvideo` , DROP COLUMN `CTlumbar` , DROP COLUMN `CTGeneticShare` , DROP COLUMN `CTGenetic` , DROP COLUMN `CTDNA` , DROP COLUMN `CTneuro` , DROP COLUMN `CTreasearch` , ADD COLUMN `HIPAA` TINYINT(4) NULL DEFAULT NULL  AFTER `ConsentType` ;

CREATE  TABLE IF NOT EXISTS `lava_crms`.`crms_file` (
  `id` INT(11) NOT NULL ,
  `pidn` INT(11) NULL DEFAULT NULL COMMENT '	' ,
  `enroll_stat_id` INT(11) NULL DEFAULT NULL COMMENT '	' ,
  `vid` INT(11) NULL DEFAULT NULL COMMENT '		' ,
  `instr_id` INT(11) NULL DEFAULT NULL ,
  PRIMARY KEY (`id`) ,
  INDEX `fk_crms_file__pidn` (`pidn` ASC) ,
  INDEX `fk_crms_file__enroll_stat_id` (`enroll_stat_id` ASC) ,
  INDEX `fk_crms_file__vid` (`vid` ASC) ,
  INDEX `fk_crms_file__instr_id` (`instr_id` ASC) ,
  CONSTRAINT `fk_crms_file__pidn`
    FOREIGN KEY (`pidn` )
    REFERENCES `lava_crms`.`patient` (`PIDN` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `fk_crms_file__enroll_stat_id`
    FOREIGN KEY (`enroll_stat_id` )
    REFERENCES `lava_crms`.`enrollmentstatus` (`EnrollStatID` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `fk_crms_file__vid`
    FOREIGN KEY (`vid` )
    REFERENCES `lava_crms`.`visit` (`VID` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `fk_crms_file__instr_id`
    FOREIGN KEY (`instr_id` )
    REFERENCES `lava_crms`.`instrumenttracking` (`InstrID` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB
DEFAULT CHARACTER SET = latin1
COLLATE = latin1_swedish_ci;

CREATE  TABLE IF NOT EXISTS `lava_crms`.`cbt_tracking` (
  `instr_id` INT(11) NOT NULL ,
  `filename` VARCHAR(500) NULL DEFAULT NULL ,
  `task` VARCHAR(25) NULL DEFAULT NULL ,
  `version` VARCHAR(10) NULL DEFAULT NULL ,
  `version_date` DATE NULL DEFAULT NULL ,
  `language` VARCHAR(10) NULL DEFAULT NULL ,
  `form` VARCHAR(10) NULL DEFAULT NULL ,
  `adult_child` VARCHAR(10) NULL DEFAULT NULL ,
  PRIMARY KEY (`instr_id`) )
ENGINE = InnoDB
DEFAULT CHARACTER SET = latin1
COLLATE = latin1_swedish_ci;

CREATE  TABLE IF NOT EXISTS `lava_crms`.`prot_config_option` (
  `option_id` INT(11) NOT NULL AUTO_INCREMENT ,
  `parent_id` INT(11) NOT NULL ,
  `ProjName` VARCHAR(75) NOT NULL ,
  `default_option` TINYINT(1) NULL DEFAULT NULL ,
  `label` VARCHAR(25) NOT NULL ,
  `notes` VARCHAR(255) NULL DEFAULT NULL ,
  `eff_date` DATE NULL DEFAULT NULL ,
  `exp_date` DATE NULL DEFAULT NULL ,
  `modified` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP ,
  PRIMARY KEY (`option_id`) ,
  INDEX `fk_prot_config_option__parent_id` (`parent_id` ASC) ,
  CONSTRAINT `fk_prot_config_option__parent_id`
    FOREIGN KEY (`parent_id` )
    REFERENCES `lava_crms`.`prot_node_config` (`node_id` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB
DEFAULT CHARACTER SET = latin1
COLLATE = latin1_swedish_ci;

CREATE  TABLE IF NOT EXISTS `lava_crms`.`prot_instr` (
  `node_id` INT(11) NOT NULL AUTO_INCREMENT ,
  `InstrID` INT(11) NULL DEFAULT NULL ,
  `collect_anchor_date` DATE NULL DEFAULT NULL ,
  `collect_win_start` DATE NULL DEFAULT NULL ,
  `collect_win_end` DATE NULL DEFAULT NULL ,
  PRIMARY KEY (`node_id`) ,
  INDEX `fk_prot_instr__node_id` (`node_id` ASC) ,
  INDEX `fk_prot_instr__InstrID` (`InstrID` ASC) ,
  CONSTRAINT `fk_prot_instr__node_id`
    FOREIGN KEY (`node_id` )
    REFERENCES `lava_crms`.`prot_node` (`node_id` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `fk_prot_instr__InstrID`
    FOREIGN KEY (`InstrID` )
    REFERENCES `lava_crms`.`instrumenttracking` (`InstrID` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB
DEFAULT CHARACTER SET = latin1
COLLATE = latin1_swedish_ci;

CREATE  TABLE IF NOT EXISTS `lava_crms`.`prot_instr_config` (
  `node_id` INT(11) NOT NULL AUTO_INCREMENT ,
  `optional` TINYINT(1) NULL DEFAULT NULL ,
  `category` VARCHAR(25) NULL DEFAULT NULL ,
  `collect_win_def` TINYINT(1) NULL DEFAULT NULL ,
  `collect_win_prot_visit_conf_id` INT(11) NULL DEFAULT NULL ,
  `collect_win_size` SMALLINT(6) NULL DEFAULT NULL ,
  `collect_win_offset` SMALLINT(6) NULL DEFAULT NULL ,
  `default_comp_status` VARCHAR(25) NULL DEFAULT NULL ,
  `default_comp_reason` VARCHAR(25) NULL DEFAULT NULL ,
  `default_comp_note` VARCHAR(100) NULL DEFAULT NULL ,
  PRIMARY KEY (`node_id`) ,
  INDEX `fk_prot_instr_config__node_id` (`node_id` ASC) ,
  INDEX `fk_prot_instr_config__collect_win_prot_visit_conf_id` (`collect_win_prot_visit_conf_id` ASC) ,
  CONSTRAINT `fk_prot_instr_config__node_id`
    FOREIGN KEY (`node_id` )
    REFERENCES `lava_crms`.`prot_node_config` (`node_id` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `fk_prot_instr_config__collect_win_prot_visit_conf_id`
    FOREIGN KEY (`collect_win_prot_visit_conf_id` )
    REFERENCES `lava_crms`.`prot_visit_config` (`node_id` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB
DEFAULT CHARACTER SET = latin1
COLLATE = latin1_swedish_ci;

CREATE  TABLE IF NOT EXISTS `lava_crms`.`prot_instr_config_option` (
  `option_id` INT(11) NOT NULL AUTO_INCREMENT ,
  `instr_type` VARCHAR(25) NULL DEFAULT NULL ,
  PRIMARY KEY (`option_id`) ,
  INDEX `fk_prot_instr_config_option__option_id` (`option_id` ASC) ,
  CONSTRAINT `fk_prot_instr_config_option__option_id`
    FOREIGN KEY (`option_id` )
    REFERENCES `lava_crms`.`prot_config_option` (`option_id` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB
DEFAULT CHARACTER SET = latin1
COLLATE = latin1_swedish_ci;

CREATE  TABLE IF NOT EXISTS `lava_crms`.`prot_node` (
  `node_id` INT(11) NOT NULL AUTO_INCREMENT ,
  `parent_id` INT(11) NULL DEFAULT NULL ,
  `config_node_id` INT(11) NOT NULL ,
  `list_order` INT(11) NULL DEFAULT NULL ,
  `PIDN` INT(11) NOT NULL ,
  `ProjName` VARCHAR(75) NOT NULL ,
  `strategy` SMALLINT(6) NOT NULL ,
  `curr_status` VARCHAR(25) NULL DEFAULT NULL ,
  `curr_reason` VARCHAR(25) NULL DEFAULT NULL ,
  `curr_note` VARCHAR(100) NULL DEFAULT NULL ,
  `comp_status` VARCHAR(25) NULL DEFAULT NULL ,
  `comp_reason` VARCHAR(25) NULL DEFAULT NULL ,
  `comp_note` VARCHAR(100) NULL DEFAULT NULL ,
  `comp_by` VARCHAR(25) NULL DEFAULT NULL ,
  `comp_date` DATE NULL DEFAULT NULL ,
  `sched_win_status` VARCHAR(25) NULL DEFAULT NULL ,
  `sched_win_reason` VARCHAR(25) NULL DEFAULT NULL ,
  `sched_win_note` VARCHAR(100) NULL DEFAULT NULL ,
  `collect_win_status` VARCHAR(25) NULL DEFAULT NULL ,
  `collect_win_reason` VARCHAR(25) NULL DEFAULT NULL ,
  `collect_win_note` VARCHAR(100) NULL DEFAULT NULL ,
  `assign_desc` VARCHAR(100) NULL DEFAULT NULL ,
  `notes` VARCHAR(255) NULL DEFAULT NULL ,
  `modified` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP ,
  PRIMARY KEY (`node_id`) ,
  INDEX `fk_prot_node__parent_id` (`parent_id` ASC) ,
  INDEX `fk_prot_node__config_node_id` (`config_node_id` ASC) ,
  CONSTRAINT `fk_prot_node__parent_id`
    FOREIGN KEY (`parent_id` )
    REFERENCES `lava_crms`.`prot_node` (`node_id` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `fk_prot_node__config_node_id`
    FOREIGN KEY (`config_node_id` )
    REFERENCES `lava_crms`.`prot_node_config` (`node_id` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB
DEFAULT CHARACTER SET = latin1
COLLATE = latin1_swedish_ci;

CREATE  TABLE IF NOT EXISTS `lava_crms`.`prot_node_config` (
  `node_id` INT(11) NOT NULL AUTO_INCREMENT ,
  `parent_id` INT(11) NULL DEFAULT NULL ,
  `list_order` INT(11) NULL DEFAULT NULL ,
  `ProjName` VARCHAR(75) NOT NULL ,
  `label` VARCHAR(25) NOT NULL ,
  `summary` VARCHAR(100) NULL DEFAULT NULL ,
  `notes` VARCHAR(255) NULL DEFAULT NULL ,
  `eff_date` DATE NULL DEFAULT NULL ,
  `exp_date` DATE NULL DEFAULT NULL ,
  `modified` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP ,
  PRIMARY KEY (`node_id`) ,
  INDEX `fk_prot_node_config__parent_id` (`parent_id` ASC) ,
  CONSTRAINT `fk_prot_node_config__parent_id`
    FOREIGN KEY (`parent_id` )
    REFERENCES `lava_crms`.`prot_node_config` (`node_id` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB
DEFAULT CHARACTER SET = latin1
COLLATE = latin1_swedish_ci;

CREATE  TABLE IF NOT EXISTS `lava_crms`.`prot_protocol` (
  `node_id` INT(11) NOT NULL AUTO_INCREMENT ,
  `assigned_date` DATE NULL DEFAULT NULL ,
  `EnrollStatID` INT(11) NULL DEFAULT NULL ,
  PRIMARY KEY (`node_id`) ,
  INDEX `fk_prot_protocol__node_id` (`node_id` ASC) ,
  INDEX `fk_prot_protocol__EnrollStatID` (`EnrollStatID` ASC) ,
  CONSTRAINT `fk_prot_protocol__node_id`
    FOREIGN KEY (`node_id` )
    REFERENCES `lava_crms`.`prot_node` (`node_id` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `fk_prot_protocol__EnrollStatID`
    FOREIGN KEY (`EnrollStatID` )
    REFERENCES `lava_crms`.`enrollmentstatus` (`EnrollStatID` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB
DEFAULT CHARACTER SET = latin1
COLLATE = latin1_swedish_ci;

CREATE  TABLE IF NOT EXISTS `lava_crms`.`prot_protocol_config` (
  `node_id` INT(11) NOT NULL AUTO_INCREMENT ,
  `category` VARCHAR(25) NULL DEFAULT NULL ,
  `first_prot_tp_conf_id` INT(11) NULL DEFAULT NULL ,
  PRIMARY KEY (`node_id`) ,
  INDEX `fk_prot_protocol_config__node_id` (`node_id` ASC) ,
  CONSTRAINT `fk_prot_protocol_config__node_id`
    FOREIGN KEY (`node_id` )
    REFERENCES `lava_crms`.`prot_node_config` (`node_id` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB
DEFAULT CHARACTER SET = latin1
COLLATE = latin1_swedish_ci;

CREATE  TABLE IF NOT EXISTS `lava_crms`.`prot_tp` (
  `node_id` INT(11) NOT NULL AUTO_INCREMENT ,
  `sched_anchor_date` DATE NULL DEFAULT NULL ,
  `sched_win_start` DATE NULL DEFAULT NULL ,
  `sched_win_end` DATE NULL DEFAULT NULL ,
  `pri_prot_visit_id` INT(11) NULL DEFAULT NULL ,
  `collect_anchor_date` DATE NULL DEFAULT NULL ,
  `collect_win_start` DATE NULL DEFAULT NULL ,
  `collect_win_end` DATE NULL DEFAULT NULL ,
  PRIMARY KEY (`node_id`) ,
  INDEX `fk_prot_tp__node_id` (`node_id` ASC) ,
  CONSTRAINT `fk_prot_tp__node_id`
    FOREIGN KEY (`node_id` )
    REFERENCES `lava_crms`.`prot_node` (`node_id` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB
DEFAULT CHARACTER SET = latin1
COLLATE = latin1_swedish_ci;

CREATE  TABLE IF NOT EXISTS `lava_crms`.`prot_tp_config` (
  `node_id` INT(11) NOT NULL AUTO_INCREMENT ,
  `optional` TINYINT(1) NULL DEFAULT NULL ,
  `sched_win_rel_tp_id` INT(11) NULL DEFAULT NULL ,
  `sched_win_rel_amt` SMALLINT(6) NULL DEFAULT NULL ,
  `sched_win_rel_units` SMALLINT(6) NULL DEFAULT NULL ,
  `sched_win_rel_mode` SMALLINT(6) NULL DEFAULT NULL ,
  `sched_win_days_from_start` SMALLINT(6) NULL DEFAULT NULL ,
  `sched_win_size` SMALLINT(6) NULL DEFAULT NULL ,
  `sched_win_offset` SMALLINT(6) NULL DEFAULT NULL ,
  `duration` SMALLINT(6) NULL DEFAULT NULL ,
  `sched_auto` TINYINT(1) NULL DEFAULT NULL ,
  `pri_prot_visit_conf_id` INT(11) NULL DEFAULT NULL ,
  `collect_win_def` TINYINT(1) NULL DEFAULT NULL ,
  `collect_win_size` SMALLINT(6) NULL DEFAULT NULL ,
  `collect_win_offset` SMALLINT(6) NULL DEFAULT NULL ,
  `repeating` TINYINT(1) NULL DEFAULT NULL ,
  `rpt_interval` SMALLINT(6) NULL DEFAULT NULL ,
  `rpt_init_num` SMALLINT(6) NULL DEFAULT NULL ,
  `rpt_create_auto` TINYINT(1) NULL DEFAULT NULL ,
  PRIMARY KEY (`node_id`) ,
  INDEX `fk_prot_tp_config__node_id` (`node_id` ASC) ,
  INDEX `fk_prot_tp_config__sched_win_rel_tp_id` (`sched_win_rel_tp_id` ASC) ,
  CONSTRAINT `fk_prot_tp_config__node_id`
    FOREIGN KEY (`node_id` )
    REFERENCES `lava_crms`.`prot_node_config` (`node_id` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `fk_prot_tp_config__sched_win_rel_tp_id`
    FOREIGN KEY (`sched_win_rel_tp_id` )
    REFERENCES `lava_crms`.`prot_tp_config` (`node_id` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB
DEFAULT CHARACTER SET = latin1
COLLATE = latin1_swedish_ci;

CREATE  TABLE IF NOT EXISTS `lava_crms`.`prot_visit` (
  `node_id` INT(11) NOT NULL AUTO_INCREMENT ,
  `VID` INT(11) NULL DEFAULT NULL ,
  PRIMARY KEY (`node_id`) ,
  INDEX `fk_prot_visit__node_id` (`node_id` ASC) ,
  INDEX `fk_prot_visit__VID` (`VID` ASC) ,
  CONSTRAINT `fk_prot_visit__node_id`
    FOREIGN KEY (`node_id` )
    REFERENCES `lava_crms`.`prot_node` (`node_id` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `fk_prot_visit__VID`
    FOREIGN KEY (`VID` )
    REFERENCES `lava_crms`.`visit` (`VID` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB
DEFAULT CHARACTER SET = latin1
COLLATE = latin1_swedish_ci;

CREATE  TABLE IF NOT EXISTS `lava_crms`.`prot_visit_config` (
  `node_id` INT(11) NOT NULL AUTO_INCREMENT ,
  `optional` TINYINT(1) NULL DEFAULT NULL ,
  `category` VARCHAR(25) NULL DEFAULT NULL ,
  PRIMARY KEY (`node_id`) ,
  INDEX `fk_prot_visit_config__node_id` (`node_id` ASC) ,
  CONSTRAINT `fk_prot_visit_config__node_id`
    FOREIGN KEY (`node_id` )
    REFERENCES `lava_crms`.`prot_node_config` (`node_id` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB
DEFAULT CHARACTER SET = latin1
COLLATE = latin1_swedish_ci;

CREATE  TABLE IF NOT EXISTS `lava_crms`.`prot_visit_config_option` (
  `option_id` INT(11) NOT NULL AUTO_INCREMENT ,
  `ProjName` VARCHAR(75) NULL DEFAULT NULL ,
  `visit_type` VARCHAR(25) NULL DEFAULT NULL ,
  PRIMARY KEY (`option_id`) ,
  INDEX `fk_prot_visit_config_option__option_id` (`option_id` ASC) ,
  CONSTRAINT `fk_prot_visit_config_option__option_id`
    FOREIGN KEY (`option_id` )
    REFERENCES `lava_crms`.`prot_config_option` (`option_id` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB
DEFAULT CHARACTER SET = latin1
COLLATE = latin1_swedish_ci;


-- -----------------------------------------------------
-- Placeholder table for view `lava_crms`.`vwrptprojectpatientstatus`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `lava_crms`.`vwrptprojectpatientstatus` (`PIDN` INT, `FullNameRev` INT, `AGE` INT, `Gender` INT, `ProjName` INT, `StatusDate` INT, `Status` INT, `StatusNote` INT, `StatusOrder` INT, `ProjUnitDesc` INT, `Project` INT, `Unit` INT, `UnitOrder` INT);

-- -----------------------------------------------------
-- Placeholder table for view `lava_crms`.`vwrptprojectvisitlist`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `lava_crms`.`vwrptprojectvisitlist` (`PIDN` INT, `FullNameRev` INT, `TransLanguage` INT, `Gender` INT, `AGE` INT, `VLocation` INT, `VType` INT, `VWith` INT, `VDate` INT, `VStatus` INT, `ProjName` INT, `VNotes` INT, `VDateNoTime` INT);

-- -----------------------------------------------------
-- Placeholder table for view `lava_crms`.`lq_view_demographics`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `lava_crms`.`lq_view_demographics` (`PIDN_demographics` INT, `DOB` INT, `AGE` INT, `Gender` INT, `Hand` INT, `Deceased` INT, `DOD` INT, `PrimaryLanguage` INT, `TestingLanguage` INT, `TransNeeded` INT, `TransLanguage` INT);

-- -----------------------------------------------------
-- Placeholder table for view `lava_crms`.`patient_age`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `lava_crms`.`patient_age` (`PIDN` INT, `AGE` INT);


USE `lava_crms`;

-- -----------------------------------------------------
-- View `lava_crms`.`vwrptprojectpatientstatus`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `lava_crms`.`vwrptprojectpatientstatus`;
USE `lava_crms`;
CREATE  OR REPLACE ALGORITHM=UNDEFINED  SQL SECURITY DEFINER VIEW `vwrptprojectpatientstatus` AS select `p`.`PIDN` AS `PIDN`,`p`.`FullNameRev` AS `FullNameRev`,`pa`.`AGE` AS `AGE`,`p`.`Gender` AS `Gender`,`lps`.`ProjName` AS `ProjName`,`lps`.`LatestDate` AS `StatusDate`,`lps`.`LatestDesc` AS `Status`,`lps`.`LatestNote` AS `StatusNote`,(case `lps`.`LatestDesc` when _utf8'ACTIVE' then 0 when _utf8'FOLLOW-UP' then 1 when _utf8'CANCELED' then 5 when _utf8'CLOSED' then 6 when _utf8'INACTIVE' then 4 when _utf8'PRE-APPOINTMENT' then 2 when _utf8'PENDING' then 3 when _utf8'ENROLLED' then 7 when _utf8'DECEASED' then 8 when _utf8'DECEASED-PERFORMED' then 9 when _utf8'DECEASED-NOT PERFORMED' then 10 when _utf8'REFERRED' then 11 when _utf8'ELIGIBLE' then 12 when _utf8'INELIGIBLE' then 13 when _utf8'WITHDREW' then 14 when _utf8'EXCLUDED' then 15 when _utf8'DECLINED' then 16 else 17 end) AS `StatusOrder`,`pu`.`Project` AS `ProjUnitDesc`,`pu`.`Project` AS `Project`,_utf8'OVERALL' AS `Unit`,1 AS `UnitOrder` from ((`patient` `p` join `enrollmentstatus` `lps` on((`p`.`PIDN` = `lps`.`PIDN`))) join `patient_age` `pa` on ((`p`.`PIDN` = `pa`.`PIDN`)) join `projectunit` `pu` on((`lps`.`ProjName` = `pu`.`ProjUnitDesc`))) union select `p`.`PIDN` AS `PIDN`,`p`.`FullNameRev` AS `FullNameRev`,`pa`.`AGE` AS `AGE`,`p`.`Gender` AS `Gender`,`lps`.`ProjName` AS `ProjName`,`lps`.`LatestDate` AS `LatestDate`,`lps`.`LatestDesc` AS `LatestDesc`,`lps`.`LatestNote` AS `StatusNote`,(case `lps`.`LatestDesc` when _utf8'ACTIVE' then 0 when _utf8'FOLLOW-UP' then 1 when _utf8'CANCELED' then 5 when _utf8'CLOSED' then 6 when _utf8'INACTIVE' then 4 when _utf8'PRE-APPOINTMENT' then 2 when _utf8'PENDING' then 3 when _utf8'ENROLLED' then 7 when _utf8'DECEASED' then 8 when _utf8'DECEASED-PERFORMED' then 9 when _utf8'DECEASED-NOT PERFORMED' then 10 when _utf8'REFERRED' then 11 when _utf8'ELIGIBLE' then 12 when _utf8'INELIGIBLE' then 13 when _utf8'WITHDREW' then 14 when _utf8'EXCLUDED' then 15 when _utf8'DECLINED' then 16 else 17 end) AS `StatusOrder`,`pu`.`ProjUnitDesc` AS `ProjUnitDesc`,`pu`.`Project` AS `Project`,`pu`.`Unit` AS `Unit`,2 AS `UnitOrder` from ((`patient` `p` join `enrollmentstatus` `lps` on((`p`.`PIDN` = `lps`.`PIDN`))) join `patient_age` `pa` on ((`p`.`PIDN` = `pa`.`PIDN`)) join `projectunit` `pu` on((`lps`.`ProjName` = `pu`.`ProjUnitDesc`))) where (`pu`.`Unit` is not null);


USE `lava_crms`;

-- -----------------------------------------------------
-- View `lava_crms`.`vwrptprojectvisitlist`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `lava_crms`.`vwrptprojectvisitlist`;
USE `lava_crms`;
CREATE  OR REPLACE ALGORITHM=UNDEFINED  SQL SECURITY DEFINER VIEW `vwrptprojectvisitlist` AS select `p`.`PIDN` AS `PIDN`,`p`.`FullNameRev` AS `FullNameRev`,`p`.`TransLanguage` AS `TransLanguage`,`p`.`Gender` AS `Gender`,`pa`.`AGE` AS `AGE`,`v`.`VLocation` AS `VLocation`,`v`.`VType` AS `VType`,`v`.`VWith` AS `VWith`,`v`.`VDate` AS `VDate`,`v`.`VStatus` AS `VStatus`,`v`.`ProjName` AS `ProjName`,`v`.`VNotes` AS `VNotes`,cast(`v`.`VDate` as date) AS `VDateNoTime` from (`patient` `p` join `visit` `v` on((`p`.`PIDN` = `v`.`PIDN`)) join `patient_age` `pa` on ((`p`.`PIDN` = `pa`.`PIDN`))) where (not((`v`.`VStatus` like _latin1'%CANC%')));


USE `lava_crms`;

-- -----------------------------------------------------
-- View `lava_crms`.`lq_view_demographics`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `lava_crms`.`lq_view_demographics`;
USE `lava_crms`;
CREATE  OR REPLACE ALGORITHM=UNDEFINED SQL SECURITY DEFINER VIEW `lava_crms`.`lq_view_demographics` AS select `lava_crms`.`patient`.`PIDN` AS `PIDN_demographics`,`lava_crms`.`patient`.`DOB` AS `DOB`,`lava_crms`.`patient_age`.`AGE` AS `AGE`,
`lava_crms`.`patient`.`Gender` AS `Gender`,`lava_crms`.`patient`.`Hand` AS `Hand`,
`lava_crms`.`patient`.`Deceased` AS `Deceased`,`lava_crms`.`patient`.`DOD` AS `DOD`,
`lava_crms`.`patient`.`PrimaryLanguage` AS `PrimaryLanguage`,
`lava_crms`.`patient`.`TestingLanguage` AS `TestingLanguage`,
`lava_crms`.`patient`.`TransNeeded` AS `TransNeeded`,`lava_crms`.`patient`.`TransLanguage` AS `TransLanguage` 
from `lava_crms`.`patient` inner join `lava_crms`.`patient_age` on `lava_crms`.`patient`.`PIDN` = `lava_crms`.`patient_age`.`PIDN` where (`lava_crms`.`patient`.`PIDN` > 0);


USE `lava_crms`;

-- -----------------------------------------------------
-- View `lava_crms`.`patient_age`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `lava_crms`.`patient_age`;
USE `lava_crms`;
CREATE  OR REPLACE VIEW `lava_crms`.`patient_age` AS
SELECT `PIDN`, ((if(isnull(date_format(`DOD`,'%Y')), date_format(now(),'%Y'), date_format(`DOD`,'%Y')) - date_format(`DOB`,'%Y')) - (if(isnull(date_format(`DOD`,'00-%m-%d')), date_format(now(),'00-%m-%d'), date_format(`DOD`,'00-%m-%d')) < date_format(`DOB`,'00-%m-%d'))) as `AGE`
FROM `patient` 
;

USE `lava_crms`;
DROP procedure IF EXISTS `lava_crms`.`lq_after_set_linkdata`;

DELIMITER $$
USE `lava_crms`$$
CREATE  PROCEDURE `lq_after_set_linkdata`(user_name varchar(50), host_name varchar(25),method VARCHAR(25))
BEGIN
IF method = 'VISIT' THEN
  UPDATE temp_linkdata1, visit 
  SET temp_linkdata1.pidn = visit.PIDN, temp_linkdata1.link_date = visit.VDATE, temp_linkdata1.link_type = method 
  WHERE temp_linkdata1.link_id = visit.VID;
ELSEIF method = 'INSTRUMENT' THEN
  UPDATE temp_linkdata1, instrumenttracking 
  SET temp_linkdata1.pidn = instrumenttracking.PIDN, temp_linkdata1.link_date = instrumenttracking.DCDATE, temp_linkdata1.link_type = method 
  WHERE temp_linkdata1.link_id = instrumenttracking.InstrID;
ELSE
  UPDATE temp_linkdata1 SET link_type = method;
END IF;

CREATE TEMPORARY TABLE temp_linkdata (
  pidn INTEGER NOT NULL,
  link_date DATE NOT NULL,
  link_id INTEGER NOT NULL,
  link_type VARCHAR(50) NOT NULL);

IF method = 'PIDN_DATE' THEN
  INSERT INTO temp_linkdata(pidn,link_date,link_id,link_type) 
  SELECT pidn,link_date, min(link_id), link_type FROM temp_linkdata1 GROUP BY pidn,link_date,link_type;
ELSE
  INSERT INTO temp_linkdata(pidn,link_date,link_id,link_type) 
  SELECT pidn,link_date, link_id, link_type FROM temp_linkdata1 GROUP BY pidn,link_date,link_id,link_type;
END IF;

ALTER TABLE temp_linkdata ADD INDEX(pidn,link_date,link_id);

END$$

DELIMITER ;
SET SQL_MODE=@OLD_SQL_MODE;
SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS;
SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS;
