SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0;
SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0;
SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='TRADITIONAL';

CREATE  TABLE IF NOT EXISTS `lava_core`.`preference` (
  `preference_id` INT(11) NOT NULL AUTO_INCREMENT ,
  `user_id` INT(10) NULL DEFAULT NULL ,
  `context` VARCHAR(255) NULL DEFAULT NULL ,
  `name` VARCHAR(255) NOT NULL ,
  `description` VARCHAR(1000) NULL DEFAULT NULL ,
  `value` VARCHAR(255) NULL DEFAULT NULL ,
  `visible` INT(11) NOT NULL DEFAULT '1' ,
  `modified` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ,
  PRIMARY KEY (`preference_id`) ,
  INDEX `preference__user_id` (`user_id` ASC) ,
  CONSTRAINT `preference__user_id`
    FOREIGN KEY (`user_id` )
    REFERENCES `lava_core`.`authuser` (`UID` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB
AUTO_INCREMENT = 1
DEFAULT CHARACTER SET = latin1
COLLATE = latin1_swedish_ci;

CREATE  TABLE IF NOT EXISTS `lava_core`.`query_objects` (
  `query_object_id` INT(11) UNSIGNED NOT NULL AUTO_INCREMENT ,
  `instance` VARCHAR(50) NOT NULL DEFAULT 'lava' ,
  `scope` VARCHAR(50) NOT NULL ,
  `module` VARCHAR(50) NOT NULL ,
  `section` VARCHAR(50) NOT NULL ,
  `target` VARCHAR(50) NOT NULL ,
  `short_desc` VARCHAR(50) NULL DEFAULT NULL ,
  `standard` TINYINT(4) NOT NULL DEFAULT '1' ,
  `primary_link` TINYINT(4) NOT NULL DEFAULT '1' ,
  `secondary_link` TINYINT(4) NOT NULL DEFAULT '1' ,
  `notes` TEXT NULL DEFAULT NULL ,
  `modified` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ,
  PRIMARY KEY (`query_object_id`) )
ENGINE = InnoDB
DEFAULT CHARACTER SET = latin1
COLLATE = latin1_swedish_ci;

DROP TABLE IF EXISTS `lava_core`.`appointment_change` ;


DELIMITER $$
USE `lava_core`$$
CREATE DEFINER=`root`@`localhost` PROCEDURE `lq_audit_event`(user_name varchar(50),host_name varchar(25),lq_query_object varchar(100),lq_query_type varchar(25))
BEGIN

DELIMITER ;
DELIMITER $$
USE `lava_core`$$
CREATE DEFINER=`root`@`localhost` PROCEDURE `lq_check_user_auth`(user_login varchar(50),host_name varchar(25))
BEGIN

DELIMITER ;
DELIMITER $$
USE `lava_core`$$
CREATE DEFINER=`root`@`localhost` PROCEDURE `lq_check_version`(pModule varchar(25), pMajor integer, pMinor integer, pFix integer)
BEGIN

DELIMITER ;
DELIMITER $$
USE `lava_core`$$
CREATE DEFINER=`root`@`localhost` PROCEDURE `lq_get_objects`(user_name varchar(50), host_name varchar(25))
BEGIN

DELIMITER ;

insert into versionhistory(`Module`,`Version`,`VersionDate`,`Major`,`Minor`,`Fix`,`UpdateRequired`)
	VALUES ('lava-core-model','3.0.4',NOW(),3,0,4,1);

insert into versionhistory(`Module`,`Version`,`VersionDate`,`Major`,`Minor`,`Fix`,`UpdateRequired`)
	VALUES ('LAVAQUERYAPP','3.0.0',NOW(),3,0,0,1);
	
SET SQL_MODE=@OLD_SQL_MODE;
SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS;
SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS;