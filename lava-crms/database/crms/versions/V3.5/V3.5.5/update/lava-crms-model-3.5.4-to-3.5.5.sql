-- in this case ProjName could be null because can attach a file to the Patient without respect to Project
ALTER TABLE crms_file ADD COLUMN ProjName varchar(75) NULL DEFAULT NULL AFTER `pidn`;

DELETE FROM versionhistory WHERE module='lava-crms-model' AND version='3.5.5';
INSERT INTO versionhistory(`Module`,`Version`,`VersionDate`,`Major`,`Minor`,`Fix`,`UpdateRequired`)
VALUES ('lava-crms-model','3.5.5','2016-02-22',3,5,5,0);


