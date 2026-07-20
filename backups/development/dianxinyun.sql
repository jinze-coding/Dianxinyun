
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `dianxinyun` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `dianxinyun`;
DROP TABLE IF EXISTS `camera_resource`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `camera_resource` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '摄像头ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `camera_name` varchar(100) NOT NULL COMMENT '摄像头名称',
  `camera_code` varchar(100) DEFAULT NULL COMMENT '摄像头编号',
  `area` varchar(50) DEFAULT NULL COMMENT '所属区域',
  `camera_type` varchar(50) DEFAULT NULL COMMENT '摄像头类型',
  `rtsp_url` varchar(500) DEFAULT NULL COMMENT 'RTSP地址',
  `online_status` tinyint DEFAULT '1' COMMENT '在线状态: 0离线 1在线',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='摄像头资源表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `camera_resource` WRITE;
/*!40000 ALTER TABLE `camera_resource` DISABLE KEYS */;
INSERT INTO `camera_resource` VALUES (1,1,'1号楼东入口摄像头','CAM-1F-EAST-01','1号楼东入口','枪机',NULL,1,0,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(2,1,'1号塔吊全景摄像头','CAM-1F-TC-01','1号塔吊','球机',NULL,1,0,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(3,1,'地下室通道摄像头','CAM-1F-B1-01','地下室一层','枪机',NULL,0,0,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(4,2,'地下二层机房摄像头','CAM-MEP-B2-01','制冷机房','半球',NULL,1,0,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(5,3,'钢材堆场摄像头','CAM-YD-01','钢材堆场','球机',NULL,1,0,'2026-07-19 21:34:35','2026-07-19 21:34:35');
/*!40000 ALTER TABLE `camera_resource` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `device_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `device_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '设备ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `device_name` varchar(100) NOT NULL COMMENT '设备名称',
  `device_code` varchar(100) DEFAULT NULL COMMENT '设备编号',
  `device_type` varchar(50) NOT NULL COMMENT '设备类型: tower_crane/elevator/monitor/other',
  `status` varchar(20) DEFAULT 'running' COMMENT '状态: running/stopped/abnormal',
  `height` varchar(50) DEFAULT NULL COMMENT '高度(塔吊)',
  `max_load` varchar(50) DEFAULT NULL COMMENT '最大载重',
  `last_report` datetime DEFAULT NULL COMMENT '最近上报时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `device_info` WRITE;
/*!40000 ALTER TABLE `device_info` DISABLE KEYS */;
INSERT INTO `device_info` VALUES (1,1,'1号塔式起重机','TC-01','tower_crane','running','65m','8t','2026-07-19 21:34:35','已完成月度维保',0,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(2,1,'1号施工电梯','EL-01','elevator','running','58m','2t','2026-07-19 21:34:35','人货两用施工升降机',0,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(3,1,'扬尘在线监测仪','ENV-01','monitor','running',NULL,NULL,'2026-07-19 21:34:35','监测PM2.5、PM10和噪声',0,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(4,2,'地下室临时排水泵组','PUMP-B2-01','other','running',NULL,NULL,'2026-07-19 21:34:35','两用一备',0,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(5,3,'材料堆场喷淋控制器','SPRAY-YD-01','other','abnormal',NULL,NULL,'2026-07-19 19:34:35','2号喷头压力偏低，待检修',0,'2026-07-19 21:34:35','2026-07-19 21:34:35');
/*!40000 ALTER TABLE `device_info` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `device_status_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `device_status_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `device_id` bigint NOT NULL COMMENT '设备ID',
  `status` varchar(20) NOT NULL COMMENT '状态',
  `load_value` varchar(50) DEFAULT NULL COMMENT '载重值',
  `height_value` varchar(50) DEFAULT NULL COMMENT '高度值',
  `wind_speed` varchar(50) DEFAULT NULL COMMENT '风速',
  `record_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备状态记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `device_status_record` WRITE;
/*!40000 ALTER TABLE `device_status_record` DISABLE KEYS */;
/*!40000 ALTER TABLE `device_status_record` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `document_folder`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `document_folder` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '目录ID',
  `project_id` bigint NOT NULL COMMENT '作业区域ID',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '历史兼容字段，一级目录固定为0',
  `folder_name` varchar(100) NOT NULL COMMENT '目录名称',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `created_by` bigint NOT NULL COMMENT '创建人ID',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `active_folder_name` varchar(100) GENERATED ALWAYS AS ((case when (`deleted` = 0) then `folder_name` else NULL end)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_folder_active_name` (`project_id`,`parent_id`,`active_folder_name`),
  KEY `idx_document_folder_project` (`project_id`,`deleted`,`parent_id`,`sort_no`),
  CONSTRAINT `chk_document_folder_root_only` CHECK ((`parent_id` = 0))
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工程资料目录';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `document_folder` WRITE;
/*!40000 ALTER TABLE `document_folder` DISABLE KEYS */;
INSERT INTO `document_folder` (`id`, `project_id`, `parent_id`, `folder_name`, `sort_no`, `created_by`, `deleted`, `create_time`, `update_time`) VALUES (1,1,0,'施工方案',0,1,0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(2,1,0,'检查表格',0,1,0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(3,1,0,'会议纪要',0,1,0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(4,2,0,'技术交底',0,1,0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(5,3,0,'材料验收',0,1,0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(6,1,0,'13',0,1,1,'2026-07-20 09:09:18','2026-07-20 09:23:48');
/*!40000 ALTER TABLE `document_folder` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `electric_box`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `electric_box` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '电箱ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `box_code` varchar(64) NOT NULL COMMENT '电箱编号',
  `box_name` varchar(100) DEFAULT NULL COMMENT '电箱名称',
  `install_location` varchar(200) NOT NULL COMMENT '安装位置',
  `responsible_electrician_id` bigint DEFAULT NULL COMMENT '负责电工ID',
  `responsible_electrician_name` varchar(50) DEFAULT NULL COMMENT '负责电工姓名',
  `safety_manager_id` bigint DEFAULT NULL COMMENT '安全负责人ID',
  `safety_manager_name` varchar(50) DEFAULT NULL COMMENT '安全负责人姓名',
  `qr_code` varchar(100) DEFAULT NULL COMMENT '内部二维码编码',
  `qr_status` varchar(20) DEFAULT 'BOUND' COMMENT '二维码状态: BOUND/DISABLED/REPLACED',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/INACTIVE',
  `public_code` varchar(100) NOT NULL COMMENT '公开只读扫码码',
  `public_access_enabled` tinyint NOT NULL DEFAULT '1' COMMENT '公开只读扫码是否启用: 0禁用 1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_electric_box_public_code` (`public_code`),
  UNIQUE KEY `uk_electric_box_project_code` (`project_id`,`box_code`,`deleted`),
  UNIQUE KEY `uk_electric_box_project_qr` (`project_id`,`qr_code`,`deleted`),
  KEY `idx_electric_box_project` (`project_id`),
  KEY `idx_electric_box_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='电箱台账表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `electric_box` WRITE;
/*!40000 ALTER TABLE `electric_box` DISABLE KEYS */;
INSERT INTO `electric_box` VALUES (1,1,'EB-1F-AP-01','1号楼东侧一级配电箱','1号楼东侧钢筋加工区',3,'周明远',4,'李若岚','EBQR-1F-AP-01','BOUND','ACTIVE','PUB-1F-AP-01',1,'负责钢筋加工区动力和照明',0,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(2,1,'EB-1F-AP-02','1号楼西侧二级配电箱','1号楼西侧木工加工区',3,'周明远',4,'李若岚','EBQR-1F-AP-02','BOUND','ACTIVE','PUB-1F-AP-02',1,'木工加工设备专用',0,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(3,1,'EB-1F-B1-01','地下室一层照明配电箱','1号楼地下室一层东通道',3,'周明远',4,'李若岚','EBQR-1F-B1-01','BOUND','ACTIVE','PUB-1F-B1-01',1,'地下室临时照明',0,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(4,1,'EB-1F-TC-01','1号塔吊专用配电箱','1号楼北侧1号塔吊基础旁',3,'周明远',4,'李若岚','EBQR-1F-TC-01','BOUND','ACTIVE','PUB-1F-TC-01',1,'塔吊动力专用配电箱',0,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(5,2,'EB-MEP-B2-01','地下二层机房配电箱','地下二层制冷机房入口',3,'周明远',4,'李若岚','EBQR-MEP-B2-01','BOUND','ACTIVE','PUB-MEP-B2-01',1,'机房安装临时用电',0,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(6,2,'EB-MEP-EL-01','施工电梯临时配电箱','2号施工电梯首层入口',3,'周明远',4,'李若岚','EBQR-MEP-EL-01','BOUND','ACTIVE','PUB-MEP-EL-01',1,'施工电梯动力专用',0,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(7,3,'EB-YD-01','材料堆场总配电箱','钢材堆场东南角防护棚内',NULL,NULL,4,'李若岚','EBQR-YD-01','BOUND','ACTIVE','PUB-YD-01',1,'材料加工及夜间照明',0,'2026-07-19 21:34:35','2026-07-19 21:34:35');
/*!40000 ALTER TABLE `electric_box` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `electric_box_inspection_scope`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `electric_box_inspection_scope` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '巡检范围记录ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `electric_box_id` bigint NOT NULL COMMENT '电箱ID',
  `included` tinyint NOT NULL DEFAULT '1' COMMENT '是否纳入日检: 0否 1是',
  `effective_date` date NOT NULL COMMENT '生效日期',
  `end_date` date DEFAULT NULL COMMENT '结束日期，空表示持续有效',
  `reason` varchar(300) DEFAULT NULL COMMENT '变更原因',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(50) DEFAULT NULL COMMENT '操作人姓名',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_box_scope_box_date` (`electric_box_id`,`effective_date`,`end_date`),
  KEY `idx_box_scope_project_date` (`project_id`,`effective_date`,`end_date`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='电箱日检范围历史';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `electric_box_inspection_scope` WRITE;
/*!40000 ALTER TABLE `electric_box_inspection_scope` DISABLE KEYS */;
INSERT INTO `electric_box_inspection_scope` VALUES (1,1,1,1,'2026-07-01',NULL,'投入使用并纳入日检',2,'陈志远','2026-07-19 21:34:35','2026-07-19 21:34:35'),(2,1,2,1,'2026-07-01',NULL,'投入使用并纳入日检',2,'陈志远','2026-07-19 21:34:35','2026-07-19 21:34:35'),(3,1,3,1,'2026-07-10',NULL,'地下室作业面启用',2,'陈志远','2026-07-19 21:34:35','2026-07-19 21:34:35'),(4,1,4,1,'2026-07-01',NULL,'塔吊安装验收后纳入日检',2,'陈志远','2026-07-19 21:34:35','2026-07-19 21:34:35'),(5,2,5,1,'2026-07-12',NULL,'机房安装作业开始',2,'陈志远','2026-07-19 21:34:35','2026-07-19 21:34:35'),(6,2,6,1,'2026-07-12',NULL,'施工电梯投入使用',2,'陈志远','2026-07-19 21:34:35','2026-07-19 21:34:35'),(7,3,7,1,'2026-07-01',NULL,'材料堆场投入使用',2,'陈志远','2026-07-19 21:34:35','2026-07-19 21:34:35');
/*!40000 ALTER TABLE `electric_box_inspection_scope` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `electric_box_qr_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `electric_box_qr_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '二维码操作日志ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `electric_box_id` bigint NOT NULL COMMENT '电箱ID',
  `box_code` varchar(64) NOT NULL COMMENT '操作时电箱编号',
  `action_type` varchar(30) NOT NULL COMMENT '操作类型: GENERATE/PRINT/REBIND/DISABLE/REMOVE',
  `qr_type` varchar(20) NOT NULL COMMENT '二维码类型: INTERNAL/PUBLIC',
  `old_qr_code` varchar(120) DEFAULT NULL COMMENT '旧二维码编码或旧公开码',
  `new_qr_code` varchar(120) DEFAULT NULL COMMENT '新二维码编码或新公开码',
  `operator_user_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `operator_username` varchar(50) DEFAULT NULL COMMENT '操作账号',
  `reason` varchar(300) DEFAULT NULL COMMENT '操作原因',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_eb_qr_log_box` (`electric_box_id`,`create_time`),
  KEY `idx_eb_qr_log_project` (`project_id`,`create_time`),
  KEY `idx_eb_qr_log_old_code` (`old_qr_code`),
  KEY `idx_eb_qr_log_new_code` (`new_qr_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='电箱二维码操作日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `electric_box_qr_log` WRITE;
/*!40000 ALTER TABLE `electric_box_qr_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `electric_box_qr_log` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `external_system_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `external_system_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `system_name` varchar(100) NOT NULL COMMENT '系统名称',
  `system_type` varchar(50) DEFAULT NULL COMMENT '系统类型: personnel/safety/device',
  `access_url` varchar(500) DEFAULT NULL COMMENT '访问地址',
  `status` varchar(20) DEFAULT 'normal' COMMENT '状态: normal/abnormal',
  `description` varchar(200) DEFAULT NULL COMMENT '描述',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='外部系统配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `external_system_config` WRITE;
/*!40000 ALTER TABLE `external_system_config` DISABLE KEYS */;
/*!40000 ALTER TABLE `external_system_config` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `file_resource`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `file_resource` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '文件ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `file_name` varchar(200) NOT NULL COMMENT '文件名',
  `file_type` varchar(50) DEFAULT NULL COMMENT '文件类型: training/document/signature/certificate/other',
  `file_path` varchar(500) NOT NULL COMMENT '文件存储路径',
  `storage_provider` varchar(20) DEFAULT NULL COMMENT '存储提供方: local/minio',
  `storage_key` varchar(500) DEFAULT NULL COMMENT '存储对象键',
  `original_file_name` varchar(255) DEFAULT NULL COMMENT '原始文件名',
  `mime_type` varchar(150) DEFAULT NULL COMMENT 'MIME 类型',
  `file_extension` varchar(20) DEFAULT NULL COMMENT '文件扩展名',
  `sha256` char(64) DEFAULT NULL COMMENT '文件 SHA-256',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小(字节)',
  `business_type` varchar(50) DEFAULT NULL COMMENT '业务类型: safety_education/person/training',
  `business_id` bigint DEFAULT NULL COMMENT '关联业务ID',
  `uploader_id` bigint DEFAULT NULL COMMENT '上传人ID',
  `status` varchar(20) DEFAULT 'UPLOADED' COMMENT '状态: UPLOADED/PENDING_CONFIRM/ARCHIVED',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_file_business` (`project_id`,`business_type`,`business_id`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件资料表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `file_resource` WRITE;
/*!40000 ALTER TABLE `file_resource` DISABLE KEYS */;
INSERT INTO `file_resource` VALUES (1,1,'1号楼临时用电巡检方案-V1.pdf','PROJECT_DATA','project-documents/1/2026-07-19/4d3ec5e3-60a7-40bf-b99b-08dfa0f97cd8.pdf','local','project-documents/1/2026-07-19/4d3ec5e3-60a7-40bf-b99b-08dfa0f97cd8.pdf','1号楼临时用电巡检方案-V1.pdf','application/pdf','pdf','1e0b3c0a3175837b6a26a2f502b2825ac1d33a3f7c9c25e89bbdb31ac9782af3',21789,'PROJECT_DOCUMENT',NULL,1,'UPLOADED','适用于主体结构阶段临时用电日常检查',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(2,1,'1号楼临时用电巡检方案-V2.pdf','PROJECT_DATA','project-documents/1/2026-07-19/7901c89a-02ce-4721-a408-a039724ed86e.pdf','local','project-documents/1/2026-07-19/7901c89a-02ce-4721-a408-a039724ed86e.pdf','1号楼临时用电巡检方案-V2.pdf','application/pdf','pdf','ac028cf93c0c4f15e5d52b9ef15ce05f62eea223fdcc2c3b56e8266869cc857a',24126,'PROJECT_DOCUMENT',NULL,1,'UPLOADED','适用于主体结构阶段临时用电日常检查',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(3,1,'材料进场验收台账.csv','FORM','project-documents/1/2026-07-19/5078d0a8-be87-462e-a42f-ecdbdd32b313.csv','local','project-documents/1/2026-07-19/5078d0a8-be87-462e-a42f-ecdbdd32b313.csv','材料进场验收台账.csv','application/octet-stream','csv','5d33118db137a454d6ae7ee06143ae576332e64c651e5f57f78c145ee6dc5044',254,'PROJECT_DOCUMENT',NULL,5,'UPLOADED','记录本周主要材料进场验收结果',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(4,1,'周例会纪要.txt','MEETING','project-documents/1/2026-07-19/a16b5053-ddb8-455a-9a7e-86bbe8095363.txt','local','project-documents/1/2026-07-19/a16b5053-ddb8-455a-9a7e-86bbe8095363.txt','周例会纪要.txt','text/plain','txt','6d7fabc1c618e2fe6af84d605837f172f3e46942808b9a9e01c21bd7b2a9992d',182,'PROJECT_DOCUMENT',NULL,5,'UPLOADED','项目周例会决议及责任事项',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(5,2,'地下室机电安装技术交底.docx','PROJECT_DATA','project-documents/2/2026-07-19/e85d12d0-c7a9-42bd-8958-a353d3eea065.docx','local','project-documents/2/2026-07-19/e85d12d0-c7a9-42bd-8958-a353d3eea065.docx','地下室机电安装技术交底.docx','application/octet-stream','docx','599f2f8ba3360c81cf52e4d735de45e5db2679d908781114a618acadc9a94235',3760,'PROJECT_DOCUMENT',NULL,1,'UPLOADED','地下二层机房及管线综合安装交底',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(6,3,'材料进场验收台账.csv','FORM','project-documents/3/2026-07-19/e9f7a468-533f-461c-8840-e62a56c60940.csv','local','project-documents/3/2026-07-19/e9f7a468-533f-461c-8840-e62a56c60940.csv','材料进场验收台账.csv','application/octet-stream','csv','5d33118db137a454d6ae7ee06143ae576332e64c651e5f57f78c145ee6dc5044',254,'PROJECT_DOCUMENT',NULL,5,'UPLOADED','场区材料进场验收记录',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(7,1,'主体结构质量检查要点.pdf','质量检查标准','/Users/js/Documents/电信云平台/backend/uploads/bf802067-f2fd-4244-ac22-f95ca799c4a6.pdf',NULL,NULL,NULL,NULL,NULL,NULL,21789,'QUALITY_DOCUMENT',NULL,1,'已上传',NULL,0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(8,1,'配电箱外观照片.jpg','INSPECTION_OUTER_PHOTO','/Users/js/Documents/电信云平台/backend/uploads/963f8bca-79bd-46be-af9c-3b6b0a3c5110.jpg',NULL,NULL,NULL,NULL,NULL,NULL,18222,'inspection_record',1,3,'已上传',NULL,0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(9,1,'配电箱内部照片.jpg','INSPECTION_INNER_PHOTO','/Users/js/Documents/电信云平台/backend/uploads/58ea0fd8-5330-43d2-ac4a-7896946a546b.jpg',NULL,NULL,NULL,NULL,NULL,NULL,14970,'inspection_record',1,3,'已上传',NULL,0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(10,1,'模板拼缝问题照片.jpg','质量问题照片','/Users/js/Documents/电信云平台/backend/uploads/63acb041-7d2d-4774-b597-f7723b14bb6f.jpg',NULL,NULL,NULL,NULL,NULL,NULL,17154,'QUALITY_ISSUE',1,1,'已上传',NULL,0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(11,1,'保护层问题照片.jpg','质量问题照片','/Users/js/Documents/电信云平台/backend/uploads/04ba8e09-787c-43bd-b7f7-e671ef055584.jpg',NULL,NULL,NULL,NULL,NULL,NULL,17479,'QUALITY_ISSUE',2,1,'已上传',NULL,0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(12,2,'桥架支架问题照片.jpg','质量问题照片','/Users/js/Documents/电信云平台/backend/uploads/4bc4bd5c-ab76-4ccf-9bc3-ec5f2107f744.jpg',NULL,NULL,NULL,NULL,NULL,NULL,15567,'QUALITY_ISSUE',3,1,'已上传',NULL,0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(13,2,'整改完成照片.jpg','质量整改照片','/Users/js/Documents/电信云平台/backend/uploads/ebf9a4bc-b5e9-402f-99c7-91023bfe5e96.jpg',NULL,NULL,NULL,NULL,NULL,NULL,14938,'QUALITY_RECTIFICATION',3,4,'已上传',NULL,0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(14,1,'洞口尺寸问题照片.jpg','质量问题照片','/Users/js/Documents/电信云平台/backend/uploads/2c6269cb-61de-4f7a-8a70-1f6a53649b21.jpg',NULL,NULL,NULL,NULL,NULL,NULL,15157,'QUALITY_ISSUE',4,1,'已上传',NULL,0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(15,1,'整改完成照片.jpg','质量整改照片','/Users/js/Documents/电信云平台/backend/uploads/f71d322f-a57d-4d8f-a651-145dbc1917c8.jpg',NULL,NULL,NULL,NULL,NULL,NULL,14938,'QUALITY_RECTIFICATION',4,4,'已上传',NULL,0,'2026-07-19 21:35:49','2026-07-19 21:35:50'),(16,1,'整改复测照片.jpg','质量整改照片','/Users/js/Documents/电信云平台/backend/uploads/9b736d23-2a8e-4a50-b75c-3b060d5c8e79.jpg',NULL,NULL,NULL,NULL,NULL,NULL,17576,'QUALITY_RECTIFICATION',4,4,'已上传',NULL,0,'2026-07-19 21:35:50','2026-07-19 21:35:50'),(17,1,'质量复查照片.jpg','质量复查照片','/Users/js/Documents/电信云平台/backend/uploads/47a9181d-b62d-44c4-86e5-f54e9bd68a66.jpg',NULL,NULL,NULL,NULL,NULL,NULL,17576,'QUALITY_REVIEW',4,1,'已上传',NULL,0,'2026-07-19 21:35:50','2026-07-19 21:35:50'),(18,1,'hiExUbohND9N0c5c29cf8107339284ed11f79a3dc3c6.xlsx','DRAWING','project-documents/1/2026-07-20/331934e9-1c81-4077-bc96-f2501547f174.xlsx','local','project-documents/1/2026-07-20/331934e9-1c81-4077-bc96-f2501547f174.xlsx','hiExUbohND9N0c5c29cf8107339284ed11f79a3dc3c6.xlsx','application/vnd.openxmlformats-officedocument.spreadsheetml.sheet','xlsx','8ec3a6a1402faea4d43b241122570bd661c0d03846a54c4ef3b427cdffa9ee71',16635,'PROJECT_DOCUMENT',NULL,1,'UPLOADED',NULL,0,'2026-07-20 09:08:50','2026-07-20 09:08:50');
/*!40000 ALTER TABLE `file_resource` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `inspection_permission_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inspection_permission_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '权限模板ID',
  `template_name` varchar(80) NOT NULL COMMENT '模板名称',
  `template_code` varchar(80) NOT NULL COMMENT '模板编码',
  `description` varchar(255) DEFAULT NULL COMMENT '说明',
  `permission_codes` text NOT NULL COMMENT '权限码CSV',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用: 1启用 0停用',
  `builtin` tinyint NOT NULL DEFAULT '0' COMMENT '是否内置模板: 1是 0否',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inspection_permission_template_code` (`template_code`),
  KEY `idx_inspection_permission_template_enabled` (`enabled`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='电箱巡检权限模板';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `inspection_permission_template` WRITE;
/*!40000 ALTER TABLE `inspection_permission_template` DISABLE KEYS */;
INSERT INTO `inspection_permission_template` VALUES (1,'项目管理员','PROJECT_ADMIN','管理电箱台账、二维码、巡检记录、月表导出和项目用户授权','BOX_VIEW,BOX_MANAGE,BOX_QR_MANAGE,BOX_PUBLIC_ACCESS,INSPECTION_DAILY_SUBMIT,INSPECTION_RECORD_VIEW,SUMMARY_VIEW,SUMMARY_EXPORT,PERMISSION_MANAGE',1,1,0,'2026-07-02 10:37:26','2026-07-12 17:49:33'),(2,'巡检记录管理员','SAFETY_ADMIN','查看项目电箱、巡检记录和月表导出，不包含用户授权','BOX_VIEW,BOX_MANAGE,BOX_QR_MANAGE,BOX_PUBLIC_ACCESS,INSPECTION_RECORD_VIEW,SUMMARY_VIEW,SUMMARY_EXPORT',1,1,0,'2026-07-02 10:37:26','2026-07-12 17:49:33'),(3,'巡检员','USER','查看项目电箱并提交日常巡检','BOX_VIEW,INSPECTION_DAILY_SUBMIT',1,1,0,'2026-07-02 10:37:26','2026-07-12 17:49:33');
/*!40000 ALTER TABLE `inspection_permission_template` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `inspection_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inspection_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '检查记录ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `electric_box_id` bigint NOT NULL COMMENT '电箱ID',
  `template_code` varchar(64) NOT NULL COMMENT '模板编码',
  `source` varchar(40) NOT NULL COMMENT '来源: ELECTRICIAN_DAILY/SAFETY_SPOT_CHECK',
  `problem_category` varchar(50) DEFAULT NULL COMMENT '安全抽查问题分类',
  `check_date` date NOT NULL COMMENT '检查日期',
  `inspector_id` bigint NOT NULL COMMENT '检查人ID',
  `inspector_name` varchar(50) DEFAULT NULL COMMENT '检查人姓名',
  `status` varchar(40) DEFAULT 'REVIEW_PENDING' COMMENT '记录状态',
  `review_status` varchar(40) DEFAULT 'PENDING' COMMENT '复核状态',
  `reviewer_id` bigint DEFAULT NULL COMMENT '复核人ID',
  `reviewer_name` varchar(50) DEFAULT NULL COMMENT '复核人姓名',
  `review_time` datetime DEFAULT NULL COMMENT '复核时间',
  `review_due_time` datetime DEFAULT NULL COMMENT '复核截止时间',
  `assigned_reviewer_id` bigint DEFAULT NULL COMMENT '分配复核人ID',
  `assigned_reviewer_name` varchar(50) DEFAULT NULL COMMENT '分配复核人姓名',
  `review_comment` varchar(1000) DEFAULT NULL COMMENT '最近一次复核意见',
  `review_overdue` tinyint NOT NULL DEFAULT '0' COMMENT '复核是否逾期: 0否 1是',
  `outer_photo_file_ids` varchar(500) DEFAULT NULL COMMENT '外观照片文件ID，逗号分隔',
  `inner_photo_file_ids` varchar(500) DEFAULT NULL COMMENT '内部照片文件ID，逗号分隔',
  `abnormal_count` int DEFAULT '0' COMMENT '异常项数量',
  `remark` varchar(1000) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_inspection_record_project_month` (`project_id`,`check_date`),
  KEY `idx_inspection_record_box_date` (`electric_box_id`,`check_date`),
  KEY `idx_inspection_record_status` (`status`),
  KEY `idx_inspection_record_review_assignment` (`project_id`,`status`,`assigned_reviewer_id`,`review_due_time`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='检查记录主表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `inspection_record` WRITE;
/*!40000 ALTER TABLE `inspection_record` DISABLE KEYS */;
INSERT INTO `inspection_record` VALUES (1,1,1,'ELECTRIC_BOX_DAILY','ELECTRICIAN_DAILY',NULL,'2026-07-19',3,'周明远','COMPLETED','NOT_REQUIRED',NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,'8','9',0,'开工前检查完成，箱体、接地和保护装置正常。',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(2,1,2,'ELECTRIC_BOX_DAILY','ELECTRICIAN_DAILY',NULL,'2026-07-19',3,'周明远','COMPLETED','NOT_REQUIRED',NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,'','',1,'漏电保护器复位不顺畅，已通知电工班组当日处理。',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(3,1,3,'ELECTRIC_BOX_DAILY','ELECTRICIAN_DAILY',NULL,'2026-07-19',3,'周明远','COMPLETED','NOT_REQUIRED',NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,'','',0,'地下室照明回路检查正常，通道无积水。',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(4,1,4,'ELECTRIC_BOX_DAILY','ELECTRICIAN_DAILY',NULL,'2026-07-18',3,'周明远','COMPLETED','NOT_REQUIRED',NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,'','',0,'塔吊专用配电箱检查正常，防雨措施有效。',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(5,1,1,'ELECTRIC_BOX_DAILY','ELECTRICIAN_DAILY',NULL,'2026-07-17',3,'周明远','COMPLETED','NOT_REQUIRED',NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,'','',0,'日常巡检完成，无异常。',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(6,2,5,'ELECTRIC_BOX_DAILY','ELECTRICIAN_DAILY',NULL,'2026-07-19',3,'周明远','COMPLETED','NOT_REQUIRED',NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,'','',0,'机房临时配电箱检查正常。',0,'2026-07-19 21:35:49','2026-07-19 21:35:49');
/*!40000 ALTER TABLE `inspection_record` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `inspection_record_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inspection_record_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '检查项记录ID',
  `record_id` bigint NOT NULL COMMENT '检查记录ID',
  `item_code` varchar(64) NOT NULL COMMENT '检查项编码',
  `item_name` varchar(100) NOT NULL COMMENT '检查项名称',
  `result` varchar(30) NOT NULL COMMENT '结果: NORMAL/ABNORMAL',
  `description` varchar(500) DEFAULT NULL COMMENT '说明',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_record_item_record` (`record_id`),
  KEY `idx_record_item_result` (`result`)
) ENGINE=InnoDB AUTO_INCREMENT=37 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='检查项结果明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `inspection_record_item` WRITE;
/*!40000 ALTER TABLE `inspection_record_item` DISABLE KEYS */;
INSERT INTO `inspection_record_item` VALUES (1,1,'APPEARANCE','内外观','NORMAL','箱体、防护棚及标识完好',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(2,1,'LEAKAGE_PROTECTOR','漏电保护器','NORMAL','试跳动作正常',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(3,1,'FUSE','熔断','NORMAL','熔断器规格匹配',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(4,1,'PROTECTIVE_ZERO','保护接零','NORMAL','接零连接可靠',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(5,1,'SOCKET_220V','220V插座','NORMAL','插座无破损',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(6,1,'SOCKET_380V','380V插座','NORMAL','插座及防护盖完好',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(7,2,'APPEARANCE','内外观','NORMAL','箱体和防护棚完好',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(8,2,'LEAKAGE_PROTECTOR','漏电保护器','ABNORMAL','测试按钮动作后复位不顺畅，已挂牌提醒',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(9,2,'FUSE','熔断','NORMAL','熔断器规格匹配',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(10,2,'PROTECTIVE_ZERO','保护接零','NORMAL','接零连接可靠',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(11,2,'SOCKET_220V','220V插座','NORMAL','插座无破损',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(12,2,'SOCKET_380V','380V插座','NORMAL','插座及防护盖完好',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(13,3,'APPEARANCE','内外观','NORMAL','箱体、防护棚及标识完好',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(14,3,'LEAKAGE_PROTECTOR','漏电保护器','NORMAL','试跳动作正常',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(15,3,'FUSE','熔断','NORMAL','熔断器规格匹配',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(16,3,'PROTECTIVE_ZERO','保护接零','NORMAL','接零连接可靠',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(17,3,'SOCKET_220V','220V插座','NORMAL','插座无破损',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(18,3,'SOCKET_380V','380V插座','NORMAL','插座及防护盖完好',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(19,4,'APPEARANCE','内外观','NORMAL','箱体、防护棚及标识完好',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(20,4,'LEAKAGE_PROTECTOR','漏电保护器','NORMAL','试跳动作正常',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(21,4,'FUSE','熔断','NORMAL','熔断器规格匹配',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(22,4,'PROTECTIVE_ZERO','保护接零','NORMAL','接零连接可靠',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(23,4,'SOCKET_220V','220V插座','NORMAL','插座无破损',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(24,4,'SOCKET_380V','380V插座','NORMAL','插座及防护盖完好',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(25,5,'APPEARANCE','内外观','NORMAL','箱体、防护棚及标识完好',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(26,5,'LEAKAGE_PROTECTOR','漏电保护器','NORMAL','试跳动作正常',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(27,5,'FUSE','熔断','NORMAL','熔断器规格匹配',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(28,5,'PROTECTIVE_ZERO','保护接零','NORMAL','接零连接可靠',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(29,5,'SOCKET_220V','220V插座','NORMAL','插座无破损',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(30,5,'SOCKET_380V','380V插座','NORMAL','插座及防护盖完好',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(31,6,'APPEARANCE','内外观','NORMAL','箱体、防护棚及标识完好',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(32,6,'LEAKAGE_PROTECTOR','漏电保护器','NORMAL','试跳动作正常',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(33,6,'FUSE','熔断','NORMAL','熔断器规格匹配',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(34,6,'PROTECTIVE_ZERO','保护接零','NORMAL','接零连接可靠',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(35,6,'SOCKET_220V','220V插座','NORMAL','插座无破损',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(36,6,'SOCKET_380V','380V插座','NORMAL','插座及防护盖完好',0,'2026-07-19 21:35:49','2026-07-19 21:35:49');
/*!40000 ALTER TABLE `inspection_record_item` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `inspection_rectification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inspection_rectification` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '整改任务ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `electric_box_id` bigint NOT NULL COMMENT '电箱ID',
  `inspection_record_id` bigint DEFAULT NULL COMMENT '来源检查记录ID',
  `record_item_id` bigint DEFAULT NULL COMMENT '来源检查项ID',
  `box_code` varchar(64) DEFAULT NULL COMMENT '电箱编号快照',
  `problem_desc` varchar(1000) NOT NULL COMMENT '问题描述',
  `problem_category` varchar(50) DEFAULT NULL COMMENT '整改问题分类',
  `requirement` varchar(1000) DEFAULT NULL COMMENT '整改要求',
  `assignee_id` bigint DEFAULT NULL COMMENT '整改责任人ID',
  `assignee_name` varchar(50) DEFAULT NULL COMMENT '整改责任人姓名',
  `deadline` date DEFAULT NULL COMMENT '整改期限',
  `status` varchar(30) DEFAULT 'PENDING' COMMENT '状态: PENDING/COMPLETED/CLOSED/REJECTED',
  `feedback` varchar(1000) DEFAULT NULL COMMENT '整改反馈',
  `rectification_photo_file_ids` varchar(500) DEFAULT NULL COMMENT '整改照片文件ID，逗号分隔',
  `completed_time` datetime DEFAULT NULL COMMENT '整改完成提交时间',
  `reviewer_id` bigint DEFAULT NULL COMMENT '复查人ID',
  `reviewer_name` varchar(50) DEFAULT NULL COMMENT '复查人姓名',
  `review_time` datetime DEFAULT NULL COMMENT '整改复查时间',
  `review_comment` varchar(1000) DEFAULT NULL COMMENT '整改复查意见',
  `reject_count` int NOT NULL DEFAULT '0' COMMENT '复查退回次数',
  `recheck_deadline` date DEFAULT NULL COMMENT '复查退回后的再次整改期限',
  `escalation_status` varchar(20) NOT NULL DEFAULT 'NONE' COMMENT '升级提醒状态: NONE/REMINDED/ESCALATED',
  `escalation_time` datetime DEFAULT NULL COMMENT '最近一次升级提醒时间',
  `escalation_note` varchar(1000) DEFAULT NULL COMMENT '最近一次升级提醒说明',
  `close_time` datetime DEFAULT NULL COMMENT '关闭时间',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_rectification_project_status` (`project_id`,`status`),
  KEY `idx_rectification_assignee` (`assignee_id`,`status`),
  KEY `idx_rectification_record` (`inspection_record_id`),
  KEY `idx_rectification_category` (`project_id`,`problem_category`,`status`),
  KEY `idx_rectification_escalation` (`project_id`,`status`,`deadline`,`escalation_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='整改闭环任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `inspection_rectification` WRITE;
/*!40000 ALTER TABLE `inspection_rectification` DISABLE KEYS */;
/*!40000 ALTER TABLE `inspection_rectification` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `inspection_rectification_review_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inspection_rectification_review_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '整改日志ID',
  `rectification_id` bigint NOT NULL COMMENT '整改任务ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `electric_box_id` bigint NOT NULL COMMENT '电箱ID',
  `inspection_record_id` bigint DEFAULT NULL COMMENT '检查记录ID',
  `action_type` varchar(40) NOT NULL COMMENT '动作: COMPLETE/CLOSE/REJECT/ASSIGN/ESCALATE',
  `from_status` varchar(30) DEFAULT NULL COMMENT '原整改状态',
  `to_status` varchar(30) DEFAULT NULL COMMENT '新整改状态',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(50) DEFAULT NULL COMMENT '操作人姓名',
  `comment` varchar(1000) DEFAULT NULL COMMENT '操作说明',
  `photo_file_ids` varchar(500) DEFAULT NULL COMMENT '关联照片ID',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_rectification_log_task` (`rectification_id`,`create_time`),
  KEY `idx_rectification_log_project` (`project_id`,`create_time`),
  KEY `idx_rectification_log_action` (`action_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='检查整改闭环留痕表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `inspection_rectification_review_log` WRITE;
/*!40000 ALTER TABLE `inspection_rectification_review_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `inspection_rectification_review_log` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `inspection_review_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inspection_review_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '复核日志ID',
  `record_id` bigint NOT NULL COMMENT '检查记录ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `electric_box_id` bigint NOT NULL COMMENT '电箱ID',
  `action_type` varchar(40) NOT NULL COMMENT '动作: ASSIGN/REASSIGN/UNASSIGN/PASS/REJECT/RECTIFY/OVERDUE',
  `from_reviewer_id` bigint DEFAULT NULL COMMENT '原复核人ID',
  `from_reviewer_name` varchar(50) DEFAULT NULL COMMENT '原复核人姓名',
  `to_reviewer_id` bigint DEFAULT NULL COMMENT '新复核人ID',
  `to_reviewer_name` varchar(50) DEFAULT NULL COMMENT '新复核人姓名',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(50) DEFAULT NULL COMMENT '操作人姓名',
  `comment` varchar(1000) DEFAULT NULL COMMENT '复核/分配意见',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_review_log_record` (`record_id`,`create_time`),
  KEY `idx_review_log_project` (`project_id`,`create_time`),
  KEY `idx_review_log_action` (`action_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='检查记录复核留痕表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `inspection_review_log` WRITE;
/*!40000 ALTER TABLE `inspection_review_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `inspection_review_log` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `inspection_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inspection_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '模板ID',
  `template_code` varchar(64) NOT NULL COMMENT '模板编码',
  `template_name` varchar(100) NOT NULL COMMENT '模板名称',
  `frequency` varchar(20) DEFAULT 'DAILY' COMMENT '频次: DAILY/MONTHLY',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/INACTIVE',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inspection_template_code` (`template_code`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='检查模板表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `inspection_template` WRITE;
/*!40000 ALTER TABLE `inspection_template` DISABLE KEYS */;
INSERT INTO `inspection_template` VALUES (1,'ELECTRIC_BOX_DAILY','电箱每日巡检','DAILY','ACTIVE','来源于上海建工电箱检查记录表',0,'2026-06-29 16:47:58','2026-06-29 16:47:58');
/*!40000 ALTER TABLE `inspection_template` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `inspection_template_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inspection_template_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '模板项ID',
  `template_id` bigint NOT NULL COMMENT '模板ID',
  `template_code` varchar(64) NOT NULL COMMENT '模板编码',
  `item_code` varchar(64) NOT NULL COMMENT '检查项编码',
  `item_name` varchar(100) NOT NULL COMMENT '检查项名称',
  `input_type` varchar(30) DEFAULT 'NORMAL_ABNORMAL' COMMENT '录入类型',
  `required` tinyint DEFAULT '1' COMMENT '是否必填',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `abnormal_requirement` varchar(300) DEFAULT NULL COMMENT '异常处理要求',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_item_code` (`template_code`,`item_code`,`deleted`),
  KEY `idx_template_item_template` (`template_id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='检查模板项表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `inspection_template_item` WRITE;
/*!40000 ALTER TABLE `inspection_template_item` DISABLE KEYS */;
INSERT INTO `inspection_template_item` VALUES (1,1,'ELECTRIC_BOX_DAILY','APPEARANCE','内外观','NORMAL_ABNORMAL',1,10,'上传问题照片并说明外观或内部异常',0,'2026-06-29 16:47:58','2026-06-29 16:47:58'),(2,1,'ELECTRIC_BOX_DAILY','LEAKAGE_PROTECTOR','漏电保护器','NORMAL_ABNORMAL',1,20,'确认漏保动作状态并提交整改',0,'2026-06-29 16:47:58','2026-06-29 16:47:58'),(3,1,'ELECTRIC_BOX_DAILY','FUSE','熔断','NORMAL_ABNORMAL',1,30,'检查熔断配置并提交整改',0,'2026-06-29 16:47:58','2026-06-29 16:47:58'),(4,1,'ELECTRIC_BOX_DAILY','PROTECTIVE_ZERO','保护接零','NORMAL_ABNORMAL',1,40,'检查保护接零连接并提交整改',0,'2026-06-29 16:47:58','2026-06-29 16:47:58'),(5,1,'ELECTRIC_BOX_DAILY','SOCKET_220V','220V插座','NORMAL_ABNORMAL',1,50,'检查220V插座并提交整改',0,'2026-06-29 16:47:58','2026-06-29 16:47:58'),(6,1,'ELECTRIC_BOX_DAILY','SOCKET_380V','380V插座','NORMAL_ABNORMAL',1,60,'检查380V插座并提交整改',0,'2026-06-29 16:47:58','2026-06-29 16:47:58');
/*!40000 ALTER TABLE `inspection_template_item` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `person_certificate`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `person_certificate` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '证件ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `person_id` bigint NOT NULL COMMENT '人员ID',
  `certificate_type` varchar(80) NOT NULL COMMENT '证件类型',
  `certificate_no` varchar(100) NOT NULL COMMENT '证件编号',
  `issue_date` date DEFAULT NULL COMMENT '发证日期',
  `expiry_date` date DEFAULT NULL COMMENT '到期日期',
  `file_id` bigint DEFAULT NULL COMMENT '证件附件ID',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_person_certificate_person` (`person_id`,`deleted`,`expiry_date`),
  KEY `idx_person_certificate_project` (`project_id`,`deleted`,`expiry_date`),
  KEY `idx_person_certificate_no` (`project_id`,`certificate_no`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人员特种作业及资格证件';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `person_certificate` WRITE;
/*!40000 ALTER TABLE `person_certificate` DISABLE KEYS */;
/*!40000 ALTER TABLE `person_certificate` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `person_entry_exit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `person_entry_exit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '进退场流水ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `person_id` bigint NOT NULL COMMENT '人员ID',
  `action_type` varchar(20) NOT NULL COMMENT '动作: ENTRY/EXIT',
  `occurred_at` datetime NOT NULL COMMENT '业务发生时间',
  `operator_id` bigint NOT NULL COMMENT '操作人ID',
  `operator_name` varchar(50) DEFAULT NULL COMMENT '操作人姓名快照',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_person_movement_person` (`person_id`,`occurred_at`),
  KEY `idx_person_movement_project` (`project_id`,`occurred_at`),
  KEY `idx_person_movement_action` (`project_id`,`action_type`,`occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人员进退场流水';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `person_entry_exit_log` WRITE;
/*!40000 ALTER TABLE `person_entry_exit_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `person_entry_exit_log` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `project_document`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_document` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '资料ID',
  `project_id` bigint NOT NULL COMMENT '作业区域ID',
  `folder_id` bigint NOT NULL DEFAULT '0' COMMENT '目录ID，0为根目录',
  `document_no` varchar(100) DEFAULT NULL COMMENT '资料编号',
  `title` varchar(200) NOT NULL COMMENT '资料名称',
  `category` varchar(40) NOT NULL DEFAULT 'PROJECT_DATA' COMMENT '历史兼容字段，正式界面不再维护',
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/ARCHIVED',
  `current_version_id` bigint DEFAULT NULL COMMENT '当前版本ID',
  `created_by` bigint NOT NULL COMMENT '上传人ID',
  `created_by_name` varchar(100) DEFAULT NULL COMMENT '上传人姓名快照',
  `remark` varchar(1000) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除/回收站',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `active_title` varchar(200) GENERATED ALWAYS AS ((case when (`deleted` = 0) then `title` else NULL end)) STORED,
  `active_document_no` varchar(100) GENERATED ALWAYS AS ((case when (`deleted` = 0) then `document_no` else NULL end)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_document_active_title` (`project_id`,`folder_id`,`active_title`),
  UNIQUE KEY `uk_project_document_active_no` (`project_id`,`active_document_no`),
  KEY `idx_project_document_project` (`project_id`,`deleted`,`status`,`update_time`),
  KEY `idx_project_document_folder` (`project_id`,`folder_id`,`deleted`,`update_time`),
  KEY `idx_project_document_no` (`project_id`,`document_no`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工程资料主表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `project_document` WRITE;
/*!40000 ALTER TABLE `project_document` DISABLE KEYS */;
INSERT INTO `project_document` (`id`, `project_id`, `folder_id`, `document_no`, `title`, `category`, `status`, `current_version_id`, `created_by`, `created_by_name`, `remark`, `deleted`, `create_time`, `update_time`) VALUES (1,1,1,'FA-LD-2026-001','1号楼临时用电巡检方案','PROJECT_DATA','ACTIVE',2,1,'系统管理员','适用于主体结构阶段临时用电日常检查',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(2,1,2,'BG-CL-2026-007','材料进场验收台账','FORM','ACTIVE',3,5,'王静怡','记录本周主要材料进场验收结果',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(3,1,3,'HY-2026-016','第16周施工协调会纪要','MEETING','ACTIVE',4,5,'王静怡','项目周例会决议及责任事项',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(4,2,4,'JD-MEP-2026-003','地下室机电安装技术交底','PROJECT_DATA','ACTIVE',5,1,'系统管理员','地下二层机房及管线综合安装交底',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(5,3,5,'BG-YD-2026-004','材料进场验收台账','FORM','ACTIVE',6,5,'王静怡','场区材料进场验收记录',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(6,1,3,'测试01','定转子库存(1)','DRAWING','ACTIVE',7,1,'系统管理员',NULL,0,'2026-07-20 09:08:50','2026-07-20 09:08:50');
/*!40000 ALTER TABLE `project_document` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `project_document_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_document_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '版本ID',
  `document_id` bigint NOT NULL COMMENT '资料ID',
  `version_no` int NOT NULL COMMENT '版本号，从1递增',
  `file_resource_id` bigint NOT NULL COMMENT '通用文件资源ID',
  `change_note` varchar(500) DEFAULT NULL COMMENT '版本说明',
  `created_by` bigint NOT NULL COMMENT '上传人ID',
  `created_by_name` varchar(100) DEFAULT NULL COMMENT '上传人姓名快照',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_document_version` (`document_id`,`version_no`),
  KEY `idx_document_version_file` (`file_resource_id`),
  KEY `idx_document_version_time` (`document_id`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工程资料版本';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `project_document_version` WRITE;
/*!40000 ALTER TABLE `project_document_version` DISABLE KEYS */;
INSERT INTO `project_document_version` VALUES (1,1,1,1,'初始版本',1,'系统管理员','2026-07-19 21:35:49'),(2,1,2,2,'增加塔吊配电箱雨后复检要求',1,'系统管理员','2026-07-19 21:35:49'),(3,2,1,3,'初始版本',5,'王静怡','2026-07-19 21:35:49'),(4,3,1,4,'初始版本',5,'王静怡','2026-07-19 21:35:49'),(5,4,1,5,'初始版本',1,'系统管理员','2026-07-19 21:35:49'),(6,5,1,6,'初始版本',5,'王静怡','2026-07-19 21:35:49'),(7,6,1,18,NULL,1,'系统管理员','2026-07-20 09:08:50');
/*!40000 ALTER TABLE `project_document_version` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `project_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '项目ID',
  `project_name` varchar(200) NOT NULL COMMENT '项目名称',
  `short_name` varchar(50) DEFAULT NULL COMMENT '项目简称',
  `area` varchar(50) DEFAULT NULL COMMENT '建筑面积(㎡)',
  `period` varchar(100) DEFAULT NULL COMMENT '工期',
  `phase` varchar(50) DEFAULT NULL COMMENT '当前阶段',
  `project_status` varchar(20) DEFAULT 'normal' COMMENT '项目状态: normal/warning/stopped',
  `safety_goal` varchar(200) DEFAULT NULL COMMENT '安全目标',
  `quality_goal` varchar(200) DEFAULT NULL COMMENT '质量目标',
  `manager` varchar(50) DEFAULT NULL COMMENT '项目经理',
  `contractor` varchar(200) DEFAULT NULL COMMENT '施工单位',
  `description` text COMMENT '项目描述',
  `start_date` date DEFAULT NULL COMMENT '开工日期',
  `end_date` date DEFAULT NULL COMMENT '预计截止日期',
  `longitude` decimal(10,6) DEFAULT NULL COMMENT '经度',
  `latitude` decimal(10,6) DEFAULT NULL COMMENT '纬度',
  `province` varchar(64) DEFAULT NULL COMMENT '省',
  `city` varchar(64) DEFAULT NULL COMMENT '市',
  `district` varchar(64) DEFAULT NULL COMMENT '区县',
  `address` varchar(500) DEFAULT NULL COMMENT '详细地址',
  `coordinate_type` varchar(32) DEFAULT 'BD09' COMMENT '坐标系类型',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `project_info` WRITE;
/*!40000 ALTER TABLE `project_info` DISABLE KEYS */;
INSERT INTO `project_info` VALUES (1,'1号楼主体结构作业区','1号楼主体','28600','2026.03-2027.08','主体结构施工','normal','重大安全事故为零','主体结构一次验收合格','陈志远','华东建设工程有限公司','覆盖1号楼主体结构、钢筋加工、模板安装和塔吊作业面。','2026-03-01','2027-08-31',121.507600,31.233200,'上海市','上海市','浦东新区','上海市浦东新区科创大道建设项目施工现场','BD09',0,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(2,'地下室机电安装作业区','地下室机电','15400','2026.06-2027.03','机电安装','normal','临时用电事故为零','机电安装一次验收合格','陈志远','华东机电安装有限公司','覆盖地下室配电房、设备机房、管线综合和施工电梯作业面。','2026-06-01','2027-03-31',121.508100,31.232700,'上海市','上海市','浦东新区','上海市浦东新区科创大道建设项目地下室施工现场','BD09',0,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(3,'场区临建及材料堆场','临建堆场','9200','2026.02-2027.06','临建使用','warning','消防和临电事故为零','材料分区堆放达标','陈志远','华东建设工程有限公司','覆盖办公生活临建、钢材堆场、周转材料区和场区临时道路。','2026-02-15','2027-06-30',121.506800,31.232100,'上海市','上海市','浦东新区','上海市浦东新区科创大道建设项目临建及材料场','BD09',0,'2026-07-19 21:34:35','2026-07-19 21:34:35');
/*!40000 ALTER TABLE `project_info` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `project_inspection_setting`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_inspection_setting` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `daily_cutoff_time` time NOT NULL DEFAULT '18:00:00' COMMENT '每日日检截止时间',
  `pre_due_reminder_minutes` int NOT NULL DEFAULT '60' COMMENT '截止前提醒分钟数',
  `review_due_hours` int NOT NULL DEFAULT '24' COMMENT '复核时限小时数',
  `rectification_days` int NOT NULL DEFAULT '3' COMMENT '整改自然日天数',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_inspection_setting` (`project_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目电箱巡检设置';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `project_inspection_setting` WRITE;
/*!40000 ALTER TABLE `project_inspection_setting` DISABLE KEYS */;
INSERT INTO `project_inspection_setting` VALUES (1,1,'18:00:00',60,24,3,1,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(2,2,'17:30:00',60,24,3,1,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(3,3,'17:00:00',90,24,2,1,'2026-07-19 21:34:35','2026-07-19 21:34:35');
/*!40000 ALTER TABLE `project_inspection_setting` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `quality_issue`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quality_issue` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '质量问题ID',
  `project_id` bigint NOT NULL COMMENT '施工区域/项目ID',
  `issue_no` varchar(40) NOT NULL COMMENT '问题编号',
  `title` varchar(200) NOT NULL COMMENT '问题标题',
  `location` varchar(200) DEFAULT NULL COMMENT '问题位置',
  `description` varchar(1000) DEFAULT NULL COMMENT '问题描述',
  `severity` varchar(20) NOT NULL DEFAULT 'NORMAL' COMMENT '严重程度: NORMAL/WARNING/DANGER',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/RECHECK/CLOSED',
  `assignee_id` bigint DEFAULT NULL COMMENT '整改负责人用户ID',
  `assignee_name` varchar(50) DEFAULT NULL COMMENT '整改负责人姓名快照',
  `deadline` date DEFAULT NULL COMMENT '整改期限',
  `rectification_description` varchar(1000) DEFAULT NULL COMMENT '整改说明',
  `rectification_photo_file_ids` varchar(1000) DEFAULT NULL COMMENT '整改照片文件ID，逗号分隔',
  `rectified_time` datetime DEFAULT NULL COMMENT '提交整改时间',
  `reviewer_id` bigint DEFAULT NULL COMMENT '复查人用户ID',
  `reviewer_name` varchar(50) DEFAULT NULL COMMENT '复查人姓名快照',
  `review_comment` varchar(1000) DEFAULT NULL COMMENT '复查意见',
  `review_time` datetime DEFAULT NULL COMMENT '复查时间',
  `created_by_id` bigint NOT NULL COMMENT '发起人用户ID',
  `created_by_name` varchar(50) DEFAULT NULL COMMENT '发起人姓名快照',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_quality_issue_no` (`issue_no`),
  KEY `idx_quality_issue_project_status` (`project_id`,`status`,`deleted`),
  KEY `idx_quality_issue_assignee` (`assignee_id`,`status`,`deleted`),
  KEY `idx_quality_issue_deadline` (`project_id`,`deadline`,`status`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='质量问题闭环';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `quality_issue` WRITE;
/*!40000 ALTER TABLE `quality_issue` DISABLE KEYS */;
INSERT INTO `quality_issue` VALUES (1,1,'Q-20260719-149235','1号楼西侧模板拼缝存在漏浆风险','1号楼六层西侧剪力墙','模板拼缝局部大于控制值，浇筑前需重新封堵并复核。','WARNING','PENDING',4,'李若岚','2026-07-19',NULL,NULL,NULL,NULL,NULL,NULL,NULL,1,'系统管理员',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(2,1,'Q-20260719-149288','梁底钢筋保护层垫块间距不均','1号楼六层3-5轴梁板','局部垫块间距偏大，钢筋班组应按方案补设并自检。','NORMAL','PENDING',4,'李若岚','2026-07-22',NULL,NULL,NULL,NULL,NULL,NULL,NULL,1,'系统管理员',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(3,2,'Q-20260719-149336','地下室桥架支架间距偏大','地下二层制冷机房北侧','两处桥架支架间距超过技术交底要求，需要增设支架。','WARNING','RECHECK',4,'李若岚','2026-07-22','已按交底要求增设两组支架，并完成紧固和防腐处理。','13','2026-07-19 21:35:49',NULL,NULL,NULL,NULL,1,'系统管理员',0,'2026-07-19 21:35:49','2026-07-19 21:35:49'),(4,1,'Q-20260719-149450','楼梯间预留洞口尺寸偏差','1号楼五层东楼梯间','预留洞口宽度比图纸要求小20毫米，需修整后复测。','WARNING','CLOSED',4,'李若岚','2026-07-22','已二次修整并复测，尺寸满足图纸要求。','16','2026-07-19 21:35:50',1,'系统管理员','复测尺寸符合图纸要求，同意关闭。','2026-07-19 21:35:50',1,'系统管理员',0,'2026-07-19 21:35:49','2026-07-19 21:35:50');
/*!40000 ALTER TABLE `quality_issue` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `quality_issue_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quality_issue_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `issue_id` bigint NOT NULL COMMENT '质量问题ID',
  `project_id` bigint NOT NULL COMMENT '施工区域/项目ID',
  `action_type` varchar(30) NOT NULL COMMENT '动作: CREATE/RECTIFY/REVIEW_PASS/REVIEW_REJECT',
  `from_status` varchar(20) DEFAULT NULL COMMENT '原状态',
  `to_status` varchar(20) DEFAULT NULL COMMENT '新状态',
  `operator_id` bigint NOT NULL COMMENT '操作人用户ID',
  `operator_name` varchar(50) DEFAULT NULL COMMENT '操作人姓名快照',
  `comment` varchar(1000) DEFAULT NULL COMMENT '操作说明',
  `photo_file_ids` varchar(1000) DEFAULT NULL COMMENT '操作照片文件ID，逗号分隔',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_quality_issue_log_issue` (`issue_id`,`create_time`),
  KEY `idx_quality_issue_log_project` (`project_id`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='质量问题操作留痕';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `quality_issue_log` WRITE;
/*!40000 ALTER TABLE `quality_issue_log` DISABLE KEYS */;
INSERT INTO `quality_issue_log` VALUES (1,1,1,'CREATE',NULL,'PENDING',1,'系统管理员','模板拼缝局部大于控制值，浇筑前需重新封堵并复核。','10','2026-07-19 21:35:49'),(2,2,1,'CREATE',NULL,'PENDING',1,'系统管理员','局部垫块间距偏大，钢筋班组应按方案补设并自检。','11','2026-07-19 21:35:49'),(3,3,2,'CREATE',NULL,'PENDING',1,'系统管理员','两处桥架支架间距超过技术交底要求，需要增设支架。','12','2026-07-19 21:35:49'),(4,3,2,'RECTIFY','PENDING','RECHECK',4,'李若岚','已按交底要求增设两组支架，并完成紧固和防腐处理。','13','2026-07-19 21:35:49'),(5,4,1,'CREATE',NULL,'PENDING',1,'系统管理员','预留洞口宽度比图纸要求小20毫米，需修整后复测。','14','2026-07-19 21:35:49'),(6,4,1,'RECTIFY','PENDING','RECHECK',4,'李若岚','已完成洞口边缘修整。','15','2026-07-19 21:35:50'),(7,4,1,'REVIEW_REJECT','RECHECK','PENDING',1,'系统管理员','现场复测仍差5毫米，请继续修整。',NULL,'2026-07-19 21:35:50'),(8,4,1,'RECTIFY','PENDING','RECHECK',4,'李若岚','已二次修整并复测，尺寸满足图纸要求。','16','2026-07-19 21:35:50'),(9,4,1,'REVIEW_PASS','RECHECK','CLOSED',1,'系统管理员','复测尺寸符合图纸要求，同意关闭。','17','2026-07-19 21:35:50');
/*!40000 ALTER TABLE `quality_issue_log` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `safety_education_batch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `safety_education_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '批次ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `batch_name` varchar(200) NOT NULL COMMENT '批次名称',
  `edu_type` varchar(50) DEFAULT '三级安全教育' COMMENT '教育类型',
  `training_time` datetime DEFAULT NULL COMMENT '培训时间',
  `training_place` varchar(100) DEFAULT NULL COMMENT '培训地点',
  `trainer` varchar(50) DEFAULT NULL COMMENT '培训讲师',
  `status` varchar(20) DEFAULT 'NOT_STARTED' COMMENT '状态: NOT_STARTED/IN_PROGRESS/COMPLETED',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `course_hours` int DEFAULT NULL COMMENT '培训课时',
  `exam_type` varchar(100) DEFAULT NULL COMMENT '考核方式',
  `training_material` varchar(200) DEFAULT NULL COMMENT '培训课件',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全教育批次表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `safety_education_batch` WRITE;
/*!40000 ALTER TABLE `safety_education_batch` DISABLE KEYS */;
/*!40000 ALTER TABLE `safety_education_batch` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `safety_education_person`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `safety_education_person` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_id` bigint NOT NULL COMMENT '批次ID',
  `person_id` bigint NOT NULL COMMENT '人员ID',
  `status` varchar(20) DEFAULT 'WAITING' COMMENT '状态: WAITING/FINISHED',
  `finish_time` datetime DEFAULT NULL COMMENT '完成时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全教育人员关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `safety_education_person` WRITE;
/*!40000 ALTER TABLE `safety_education_person` DISABLE KEYS */;
/*!40000 ALTER TABLE `safety_education_person` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `sys_operation_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL COMMENT '用户ID',
  `username` varchar(50) DEFAULT NULL COMMENT '用户名',
  `operation_type` varchar(50) NOT NULL COMMENT '操作类型',
  `operation_desc` varchar(500) DEFAULT NULL COMMENT '操作描述',
  `business_type` varchar(50) DEFAULT NULL COMMENT '业务类型',
  `business_id` bigint DEFAULT NULL COMMENT '业务ID',
  `ip_address` varchar(50) DEFAULT NULL COMMENT 'IP地址',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `sys_operation_log` WRITE;
/*!40000 ALTER TABLE `sys_operation_log` DISABLE KEYS */;
INSERT INTO `sys_operation_log` VALUES (1,1,'系统管理员','DOCUMENT_UPLOAD','上传资料《1号楼临时用电巡检方案》V1','PROJECT_DOCUMENT_1',1,'127.0.0.1','2026-07-19 21:35:49'),(2,1,'系统管理员','DOCUMENT_VERSION','上传《1号楼临时用电巡检方案》V2','PROJECT_DOCUMENT_1',1,'127.0.0.1','2026-07-19 21:35:49'),(3,5,'王静怡','DOCUMENT_UPLOAD','上传资料《材料进场验收台账》V1','PROJECT_DOCUMENT_1',2,'127.0.0.1','2026-07-19 21:35:49'),(4,5,'王静怡','DOCUMENT_UPLOAD','上传资料《第16周施工协调会纪要》V1','PROJECT_DOCUMENT_1',3,'127.0.0.1','2026-07-19 21:35:49'),(5,1,'系统管理员','DOCUMENT_UPLOAD','上传资料《地下室机电安装技术交底》V1','PROJECT_DOCUMENT_2',4,'127.0.0.1','2026-07-19 21:35:49'),(6,5,'王静怡','DOCUMENT_UPLOAD','上传资料《材料进场验收台账》V1','PROJECT_DOCUMENT_3',5,'127.0.0.1','2026-07-19 21:35:49'),(7,2,'陈志远','DOCUMENT_DOWNLOAD','下载《1号楼临时用电巡检方案》V2','PROJECT_DOCUMENT_1',1,'127.0.0.1','2026-07-19 21:35:49'),(8,5,'王静怡','DOCUMENT_DOWNLOAD','下载《材料进场验收台账》V1','PROJECT_DOCUMENT_1',2,'127.0.0.1','2026-07-19 21:35:49'),(9,1,'系统管理员','FILE_UPLOAD','上传《主体结构质量检查要点.pdf》','FILE_PROJECT_1',7,'127.0.0.1','2026-07-19 21:35:49'),(10,3,'周明远','FILE_UPLOAD','上传《配电箱外观照片.jpg》','FILE_PROJECT_1',8,'127.0.0.1','2026-07-19 21:35:49'),(11,3,'周明远','FILE_UPLOAD','上传《配电箱内部照片.jpg》','FILE_PROJECT_1',9,'127.0.0.1','2026-07-19 21:35:49'),(12,1,'系统管理员','FILE_UPLOAD','上传《模板拼缝问题照片.jpg》','FILE_PROJECT_1',10,'127.0.0.1','2026-07-19 21:35:49'),(13,1,'系统管理员','FILE_UPLOAD','上传《保护层问题照片.jpg》','FILE_PROJECT_1',11,'127.0.0.1','2026-07-19 21:35:49'),(14,1,'系统管理员','FILE_UPLOAD','上传《桥架支架问题照片.jpg》','FILE_PROJECT_2',12,'127.0.0.1','2026-07-19 21:35:49'),(15,4,'李若岚','FILE_UPLOAD','上传《整改完成照片.jpg》','FILE_PROJECT_2',13,'127.0.0.1','2026-07-19 21:35:49'),(16,1,'系统管理员','FILE_UPLOAD','上传《洞口尺寸问题照片.jpg》','FILE_PROJECT_1',14,'127.0.0.1','2026-07-19 21:35:49'),(17,4,'李若岚','FILE_UPLOAD','上传《整改完成照片.jpg》','FILE_PROJECT_1',15,'127.0.0.1','2026-07-19 21:35:49'),(18,4,'李若岚','FILE_UPLOAD','上传《整改复测照片.jpg》','FILE_PROJECT_1',16,'127.0.0.1','2026-07-19 21:35:50'),(19,1,'系统管理员','FILE_UPLOAD','上传《质量复查照片.jpg》','FILE_PROJECT_1',17,'127.0.0.1','2026-07-19 21:35:50'),(20,1,'系统管理员','DOCUMENT_PREVIEW','预览《1号楼临时用电巡检方案》V2','PROJECT_DOCUMENT_1',1,'127.0.0.1','2026-07-19 21:50:16'),(21,1,'系统管理员','FILE_DOWNLOAD','下载《主体结构质量检查要点.pdf》','FILE_PROJECT_1',7,'127.0.0.1','2026-07-19 21:50:16'),(22,1,'系统管理员','DOCUMENT_UPLOAD','上传资料《定转子库存(1)》V1','PROJECT_DOCUMENT_1',6,'127.0.0.1','2026-07-20 09:08:50');
/*!40000 ALTER TABLE `sys_operation_log` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `sys_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_name` varchar(50) NOT NULL COMMENT '角色名称',
  `role_code` varchar(50) NOT NULL COMMENT '角色编码',
  `description` varchar(200) DEFAULT NULL COMMENT '描述',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `sys_role` WRITE;
/*!40000 ALTER TABLE `sys_role` DISABLE KEYS */;
INSERT INTO `sys_role` VALUES (1,'平台管理员','PLATFORM_ADMIN','平台管理员，拥有所有权限',0,'2026-05-27 21:46:57','2026-05-27 21:46:57'),(2,'项目管理员','PROJECT_ADMIN','项目管理员，管理指定项目',0,'2026-05-27 21:46:57','2026-05-27 21:46:57'),(3,'安全管理员','SAFETY_ADMIN','安全管理员，负责安全管理',0,'2026-05-27 21:46:57','2026-05-27 21:46:57'),(4,'普通用户','USER','普通用户，仅查看权限',0,'2026-05-27 21:46:57','2026-05-27 21:46:57');
/*!40000 ALTER TABLE `sys_role` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(200) NOT NULL COMMENT '密码',
  `password_login_enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否允许账号密码登录: 1是 0否',
  `real_name` varchar(50) DEFAULT NULL COMMENT '真实姓名',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `status` tinyint DEFAULT '1' COMMENT '状态: 0禁用 1启用',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `sys_user` WRITE;
/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
INSERT INTO `sys_user` VALUES (1,'admin','$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH',1,'系统管理员','19900001000','admin@example.test',1,0,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(2,'project_admin','$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH',1,'陈志远','19900001001','project.admin@example.test',1,0,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(3,'inspector','$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH',1,'周明远','19900001002','inspector@example.test',1,0,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(4,'quality_manager','$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH',1,'李若岚','19900001003','quality@example.test',1,0,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(5,'document_manager','$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH',1,'王静怡','19900001004','document@example.test',1,0,'2026-07-19 21:34:35','2026-07-19 21:34:35');
/*!40000 ALTER TABLE `sys_user` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `sys_user_project`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_project` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `project_role_code` varchar(40) NOT NULL DEFAULT 'USER' COMMENT '项目内职责: PROJECT_ADMIN/SAFETY_ADMIN/USER',
  `inspection_permission_template_id` bigint DEFAULT NULL COMMENT '电箱巡检权限模板ID',
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '项目访问状态: ACTIVE/DISABLED',
  `status_reason` varchar(300) DEFAULT NULL COMMENT '项目授权启停原因',
  `status_changed_by` bigint DEFAULT NULL COMMENT '最近启停操作人',
  `status_changed_time` datetime DEFAULT NULL COMMENT '最近启停时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_project_user_project` (`user_id`,`project_id`),
  KEY `idx_sys_user_project_permission_template` (`inspection_permission_template_id`),
  KEY `idx_sys_user_project_status` (`project_id`,`status`,`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户项目权限表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `sys_user_project` WRITE;
/*!40000 ALTER TABLE `sys_user_project` DISABLE KEYS */;
INSERT INTO `sys_user_project` VALUES (1,1,1,'PROJECT_ADMIN',1,'ACTIVE',NULL,NULL,NULL,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(2,1,2,'PROJECT_ADMIN',1,'ACTIVE',NULL,NULL,NULL,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(3,1,3,'PROJECT_ADMIN',1,'ACTIVE',NULL,NULL,NULL,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(4,2,1,'PROJECT_ADMIN',1,'ACTIVE',NULL,NULL,NULL,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(5,2,2,'PROJECT_ADMIN',1,'ACTIVE',NULL,NULL,NULL,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(6,2,3,'PROJECT_ADMIN',1,'ACTIVE',NULL,NULL,NULL,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(7,3,1,'USER',3,'ACTIVE',NULL,NULL,NULL,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(8,3,2,'USER',3,'ACTIVE',NULL,NULL,NULL,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(9,4,1,'SAFETY_ADMIN',2,'ACTIVE',NULL,NULL,NULL,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(10,4,2,'SAFETY_ADMIN',2,'ACTIVE',NULL,NULL,NULL,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(11,5,1,'USER',3,'ACTIVE',NULL,NULL,NULL,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(12,5,3,'USER',3,'ACTIVE',NULL,NULL,NULL,'2026-07-19 21:34:35','2026-07-19 21:34:35');
/*!40000 ALTER TABLE `sys_user_project` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `sys_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `sys_user_role` WRITE;
/*!40000 ALTER TABLE `sys_user_role` DISABLE KEYS */;
INSERT INTO `sys_user_role` VALUES (1,1,1,'2026-07-19 21:34:35'),(2,2,2,'2026-07-19 21:34:35'),(3,3,4,'2026-07-19 21:34:35'),(4,4,3,'2026-07-19 21:34:35'),(5,5,4,'2026-07-19 21:34:35');
/*!40000 ALTER TABLE `sys_user_role` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `sys_user_wechat_binding`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_wechat_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '绑定ID',
  `user_id` bigint NOT NULL COMMENT '系统用户ID',
  `app_id` varchar(80) NOT NULL COMMENT '微信小程序AppID',
  `openid` varchar(128) NOT NULL COMMENT '微信OpenID',
  `unionid` varchar(128) DEFAULT NULL COMMENT '微信UnionID',
  `phone` varchar(20) DEFAULT NULL COMMENT '微信授权手机号',
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  `active_user_id` bigint GENERATED ALWAYS AS ((case when ((`status` = _utf8mb4'ACTIVE') and (`deleted` = 0)) then `user_id` else NULL end)) STORED COMMENT '同AppID有效绑定唯一键',
  `bind_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
  `last_login_time` datetime DEFAULT NULL COMMENT '最近登录时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wechat_binding_openid` (`app_id`,`openid`,`deleted`),
  UNIQUE KEY `uk_wechat_binding_active_user` (`app_id`,`active_user_id`),
  KEY `idx_wechat_binding_user` (`user_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户微信绑定';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `sys_user_wechat_binding` WRITE;
/*!40000 ALTER TABLE `sys_user_wechat_binding` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_user_wechat_binding` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `temporary_person`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `temporary_person` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '人员ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `name` varchar(50) NOT NULL COMMENT '姓名',
  `gender` varchar(10) DEFAULT NULL COMMENT '性别',
  `idcard` varchar(20) DEFAULT NULL COMMENT '身份证号',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `unit` varchar(100) DEFAULT NULL COMMENT '所属单位',
  `role` varchar(50) DEFAULT NULL COMMENT '工种',
  `entry_time` datetime DEFAULT NULL COMMENT '入场时间',
  `status` varchar(20) DEFAULT 'WAIT_EDUCATION' COMMENT '状态: WAIT_EDUCATION/EDUCATED/LEFT',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='临时人员表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `temporary_person` WRITE;
/*!40000 ALTER TABLE `temporary_person` DISABLE KEYS */;
INSERT INTO `temporary_person` VALUES (1,1,'张建国','男','310101199001010011','19910002001','华东建设劳务一队','钢筋工','2026-07-01 07:30:00','EDUCATED','已完成三级教育',0,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(2,1,'刘海峰','男','310101199002020022','19910002002','华东建设劳务一队','木工','2026-07-02 07:35:00','EDUCATED','已完成三级教育',0,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(3,1,'赵晓梅','女','310101199003030033','19910002003','华东建设劳务一队','资料员','2026-07-03 08:00:00','EDUCATED','已完成三级教育',0,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(4,2,'孙启明','男','310101199004040044','19910002004','华东机电安装班组','电工','2026-07-12 07:40:00','EDUCATED','特种作业证件已核验',0,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(5,2,'郭文杰','男','310101199005050055','19910002005','华东机电安装班组','管道工','2026-07-18 08:10:00','WAIT_EDUCATION','待完成项目级教育',0,'2026-07-19 21:34:35','2026-07-19 21:34:35'),(6,3,'何志鹏','男','310101199006060066','19910002006','场区综合班组','材料员','2026-06-28 08:00:00','EDUCATED','负责材料进出场登记',0,'2026-07-19 21:34:35','2026-07-19 21:34:35');
/*!40000 ALTER TABLE `temporary_person` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `video_access_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `video_access_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `camera_id` bigint NOT NULL COMMENT '摄像头ID',
  `access_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '访问时间',
  `ip_address` varchar(50) DEFAULT NULL COMMENT 'IP地址',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='视频访问日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `video_access_log` WRITE;
/*!40000 ALTER TABLE `video_access_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `video_access_log` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `video_layout_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `video_layout_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `layout_type` varchar(20) DEFAULT 'quad' COMMENT '布局类型: single/quad/eight/sixteen',
  `layout_data` text COMMENT '布局数据(JSON格式)',
  `is_default` tinyint DEFAULT '0' COMMENT '是否默认布局',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='视频窗口布局配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `video_layout_config` WRITE;
/*!40000 ALTER TABLE `video_layout_config` DISABLE KEYS */;
/*!40000 ALTER TABLE `video_layout_config` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `wechat_access_application`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wechat_access_application` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '申请ID',
  `app_id` varchar(80) NOT NULL COMMENT '微信小程序AppID',
  `openid` varchar(128) NOT NULL COMMENT '微信OpenID',
  `phone` varchar(20) DEFAULT NULL COMMENT '微信授权手机号',
  `real_name` varchar(50) DEFAULT NULL COMMENT '申请人姓名',
  `project_id` bigint NOT NULL COMMENT '申请项目ID',
  `source_type` varchar(40) NOT NULL DEFAULT 'ELECTRIC_BOX' COMMENT '来源类型',
  `source_id` bigint DEFAULT NULL COMMENT '来源电箱ID',
  `matched_user_id` bigint DEFAULT NULL COMMENT '匹配的系统用户ID',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
  `reviewer_id` bigint DEFAULT NULL COMMENT '审批人ID',
  `reviewer_name` varchar(50) DEFAULT NULL COMMENT '审批人姓名',
  `review_comment` varchar(300) DEFAULT NULL COMMENT '审批意见',
  `review_time` datetime DEFAULT NULL COMMENT '审批时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_wechat_application_project` (`project_id`,`status`,`create_time`),
  KEY `idx_wechat_application_openid` (`app_id`,`openid`,`project_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='微信注册和项目权限申请';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `wechat_access_application` WRITE;
/*!40000 ALTER TABLE `wechat_access_application` DISABLE KEYS */;
/*!40000 ALTER TABLE `wechat_access_application` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `wechat_message_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wechat_message_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息日志ID',
  `user_id` bigint DEFAULT NULL COMMENT '接收系统用户ID',
  `openid` varchar(128) DEFAULT NULL COMMENT '接收OpenID',
  `template_code` varchar(64) NOT NULL COMMENT '业务模板编码',
  `business_type` varchar(64) DEFAULT NULL COMMENT '业务类型',
  `business_id` bigint DEFAULT NULL COMMENT '业务ID',
  `status` varchar(20) NOT NULL COMMENT 'PENDING/SENT/SKIPPED/FAILED',
  `request_payload` text COMMENT '脱敏后的发送内容',
  `response_code` varchar(40) DEFAULT NULL COMMENT '微信响应码',
  `response_message` varchar(300) DEFAULT NULL COMMENT '微信响应说明',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '重试次数',
  `sent_time` datetime DEFAULT NULL COMMENT '发送时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_wechat_message_business` (`business_type`,`business_id`),
  KEY `idx_wechat_message_user` (`user_id`,`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='微信订阅消息发送日志';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `wechat_message_log` WRITE;
/*!40000 ALTER TABLE `wechat_message_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `wechat_message_log` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `wechat_subscription_state`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wechat_subscription_state` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '订阅状态ID',
  `user_id` bigint NOT NULL COMMENT '系统用户ID',
  `app_id` varchar(80) NOT NULL COMMENT '微信小程序AppID',
  `openid` varchar(128) NOT NULL COMMENT '微信OpenID',
  `template_code` varchar(64) NOT NULL COMMENT '业务模板编码',
  `template_id` varchar(128) DEFAULT NULL COMMENT '微信模板ID',
  `available_count` int NOT NULL DEFAULT '0' COMMENT '可发送次数',
  `status` varchar(20) NOT NULL DEFAULT 'ACCEPT' COMMENT 'ACCEPT/REJECT/BAN',
  `last_authorized_time` datetime DEFAULT NULL COMMENT '最近授权时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wechat_subscription` (`user_id`,`app_id`,`template_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='微信订阅消息授权状态';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `wechat_subscription_state` WRITE;
/*!40000 ALTER TABLE `wechat_subscription_state` DISABLE KEYS */;
/*!40000 ALTER TABLE `wechat_subscription_state` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
