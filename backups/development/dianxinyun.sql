-- MySQL dump 10.13  Distrib 8.4.10, for macos26.4 (arm64)
--
-- Host: localhost    Database: dianxinyun
-- ------------------------------------------------------
-- Server version	8.4.10

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

--
-- Table structure for table `camera_resource`
--

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
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='摄像头资源表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `camera_resource`
--

LOCK TABLES `camera_resource` WRITE;
/*!40000 ALTER TABLE `camera_resource` DISABLE KEYS */;
INSERT INTO `camera_resource` VALUES (1,1,'演示现场摄像头','DEMO-CAM-001','主体楼东入口','枪机',NULL,1,0,'2026-07-28 10:39:26','2026-07-28 10:39:26');
/*!40000 ALTER TABLE `camera_resource` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `device_info`
--

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
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `device_info`
--

LOCK TABLES `device_info` WRITE;
/*!40000 ALTER TABLE `device_info` DISABLE KEYS */;
INSERT INTO `device_info` VALUES (1,1,'演示塔式起重机','DEMO-TC-001','tower_crane','running','60m','8t','2026-07-28 10:39:26','演示设备，不对应真实现场设备',0,'2026-07-28 10:39:26','2026-07-28 10:39:26');
/*!40000 ALTER TABLE `device_info` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `device_status_record`
--

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

--
-- Dumping data for table `device_status_record`
--

LOCK TABLES `device_status_record` WRITE;
/*!40000 ALTER TABLE `device_status_record` DISABLE KEYS */;
/*!40000 ALTER TABLE `device_status_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `document_folder`
--

DROP TABLE IF EXISTS `document_folder`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `document_folder` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '历史兼容字段，一级目录固定为0',
  `folder_name` varchar(100) NOT NULL,
  `sort_no` int NOT NULL DEFAULT '0',
  `created_by` bigint NOT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `active_folder_name` varchar(100) GENERATED ALWAYS AS ((case when (`deleted` = 0) then `folder_name` else NULL end)) STORED,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_folder_active_name` (`project_id`,`parent_id`,`active_folder_name`),
  KEY `idx_document_folder_project` (`project_id`,`deleted`,`parent_id`,`sort_no`),
  CONSTRAINT `chk_document_folder_root_only` CHECK ((`parent_id` = 0))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工程资料目录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `document_folder`
--

LOCK TABLES `document_folder` WRITE;
/*!40000 ALTER TABLE `document_folder` DISABLE KEYS */;
INSERT INTO `document_folder` (`id`, `project_id`, `parent_id`, `folder_name`, `sort_no`, `created_by`, `deleted`, `create_time`, `update_time`) VALUES (1,1,0,'演示资料',0,1,0,'2026-07-28 10:40:56','2026-07-28 10:40:56');
/*!40000 ALTER TABLE `document_folder` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `electric_box`
--

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
  `qr_status` varchar(20) DEFAULT 'BOUND' COMMENT 'BOUND/DISABLED/REPLACED',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE/REMOVED',
  `public_code` varchar(100) NOT NULL COMMENT '公开只读扫码码',
  `public_access_enabled` tinyint NOT NULL DEFAULT '1' COMMENT '公开访问是否启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_electric_box_public_code` (`public_code`),
  UNIQUE KEY `uk_electric_box_project_code` (`project_id`,`box_code`,`deleted`),
  UNIQUE KEY `uk_electric_box_project_qr` (`project_id`,`qr_code`,`deleted`),
  KEY `idx_electric_box_project` (`project_id`),
  KEY `idx_electric_box_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='电箱台账表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `electric_box`
--

LOCK TABLES `electric_box` WRITE;
/*!40000 ALTER TABLE `electric_box` DISABLE KEYS */;
INSERT INTO `electric_box` VALUES (1,1,'DEMO-EB-001','演示一级配电箱','主体楼一层东侧演示区',1,'系统管理员',1,'系统管理员','DEMO-EBQR-001','BOUND','ACTIVE','DEMO-PUBLIC-001',1,'Web 与小程序扫码巡检演示电箱',0,'2026-07-28 10:39:26','2026-07-28 10:39:26');
/*!40000 ALTER TABLE `electric_box` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `electric_box_inspection_scope`
--

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
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='电箱日检范围历史';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `electric_box_inspection_scope`
--

LOCK TABLES `electric_box_inspection_scope` WRITE;
/*!40000 ALTER TABLE `electric_box_inspection_scope` DISABLE KEYS */;
INSERT INTO `electric_box_inspection_scope` VALUES (1,1,1,1,'2026-06-28',NULL,'演示电箱纳入日检',1,'系统管理员','2026-07-28 10:39:26','2026-07-28 10:39:26');
/*!40000 ALTER TABLE `electric_box_inspection_scope` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `electric_box_qr_log`
--

DROP TABLE IF EXISTS `electric_box_qr_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `electric_box_qr_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `electric_box_id` bigint NOT NULL,
  `box_code` varchar(64) NOT NULL,
  `action_type` varchar(30) NOT NULL COMMENT 'GENERATE/PRINT/REBIND/DISABLE/REMOVE',
  `qr_type` varchar(20) NOT NULL COMMENT 'INTERNAL/PUBLIC',
  `old_qr_code` varchar(120) DEFAULT NULL,
  `new_qr_code` varchar(120) DEFAULT NULL,
  `operator_user_id` bigint DEFAULT NULL,
  `operator_username` varchar(50) DEFAULT NULL,
  `reason` varchar(300) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_eb_qr_log_box` (`electric_box_id`,`create_time`),
  KEY `idx_eb_qr_log_project` (`project_id`,`create_time`),
  KEY `idx_eb_qr_log_old_code` (`old_qr_code`),
  KEY `idx_eb_qr_log_new_code` (`new_qr_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='电箱二维码操作日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `electric_box_qr_log`
--

LOCK TABLES `electric_box_qr_log` WRITE;
/*!40000 ALTER TABLE `electric_box_qr_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `electric_box_qr_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `external_system_config`
--

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

--
-- Dumping data for table `external_system_config`
--

LOCK TABLES `external_system_config` WRITE;
/*!40000 ALTER TABLE `external_system_config` DISABLE KEYS */;
/*!40000 ALTER TABLE `external_system_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `file_resource`
--

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
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件资料表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `file_resource`
--

LOCK TABLES `file_resource` WRITE;
/*!40000 ALTER TABLE `file_resource` DISABLE KEYS */;
INSERT INTO `file_resource` VALUES (1,1,'智慧工地综合演示方案.pdf','PROJECT_DATA','project-documents/1/2026-07-28/21dd4c13-1c2c-45ed-bdbb-10909d471e39.pdf','local','project-documents/1/2026-07-28/21dd4c13-1c2c-45ed-bdbb-10909d471e39.pdf','智慧工地综合演示方案.pdf','application/pdf','pdf','36e35a35174e8998fce696381e130692c12263563f57aaed6efacc9607b14051',37477,'PROJECT_DOCUMENT',NULL,1,'UPLOADED','唯一演示工程资料',0,'2026-07-28 10:40:56','2026-07-28 10:40:56'),(2,1,'演示电箱外观.png','INSPECTION_OUTER_PHOTO','/Users/js/Documents/Jinze/Dianxinyun/backend/uploads/54b82c16-b584-4dcb-9051-0eef24649993.png',NULL,NULL,NULL,NULL,NULL,NULL,127,'inspection_record',1,1,'已上传',NULL,0,'2026-07-28 10:40:56','2026-07-28 10:40:56'),(3,1,'演示电箱内部.png','INSPECTION_INNER_PHOTO','/Users/js/Documents/Jinze/Dianxinyun/backend/uploads/9c960c15-16e0-4428-8659-16776437951b.png',NULL,NULL,NULL,NULL,NULL,NULL,127,'inspection_record',1,1,'已上传',NULL,0,'2026-07-28 10:40:56','2026-07-28 10:40:56'),(4,1,'演示质量问题.png','质量问题照片','/Users/js/Documents/Jinze/Dianxinyun/backend/uploads/dad35539-54b5-487e-9c08-a27178655329.png',NULL,NULL,NULL,NULL,NULL,NULL,127,'QUALITY_ISSUE',1,1,'已上传',NULL,0,'2026-07-28 10:40:56','2026-07-28 10:40:57');
/*!40000 ALTER TABLE `file_resource` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `inspection_permission_template`
--

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
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inspection_permission_template_code` (`template_code`),
  KEY `idx_inspection_permission_template_enabled` (`enabled`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='电箱巡检权限模板';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inspection_permission_template`
--

LOCK TABLES `inspection_permission_template` WRITE;
/*!40000 ALTER TABLE `inspection_permission_template` DISABLE KEYS */;
INSERT INTO `inspection_permission_template` VALUES (1,'项目管理员','PROJECT_ADMIN','管理电箱台账、二维码、巡检记录、月表导出和项目用户授权','BOX_VIEW,BOX_MANAGE,BOX_QR_MANAGE,BOX_PUBLIC_ACCESS,INSPECTION_DAILY_SUBMIT,INSPECTION_RECORD_VIEW,SUMMARY_VIEW,SUMMARY_EXPORT,PERMISSION_MANAGE',1,1,0,'2026-07-27 19:20:42','2026-07-27 19:20:42'),(2,'巡检记录管理员','SAFETY_ADMIN','查看项目电箱、巡检记录和月表导出，不包含用户授权','BOX_VIEW,BOX_MANAGE,BOX_QR_MANAGE,BOX_PUBLIC_ACCESS,INSPECTION_RECORD_VIEW,SUMMARY_VIEW,SUMMARY_EXPORT',1,1,0,'2026-07-27 19:20:42','2026-07-27 19:20:42'),(3,'巡检员','USER','查看项目电箱并提交日常巡检','BOX_VIEW,INSPECTION_DAILY_SUBMIT',1,1,0,'2026-07-27 19:20:42','2026-07-27 19:20:42');
/*!40000 ALTER TABLE `inspection_permission_template` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `inspection_record`
--

DROP TABLE IF EXISTS `inspection_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inspection_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `electric_box_id` bigint NOT NULL,
  `template_code` varchar(64) NOT NULL,
  `source` varchar(40) NOT NULL COMMENT 'ELECTRICIAN_DAILY/SAFETY_SPOT_CHECK',
  `problem_category` varchar(50) DEFAULT NULL,
  `check_date` date NOT NULL,
  `inspector_id` bigint NOT NULL,
  `inspector_name` varchar(50) DEFAULT NULL,
  `status` varchar(40) DEFAULT 'REVIEW_PENDING',
  `review_status` varchar(40) DEFAULT 'PENDING',
  `reviewer_id` bigint DEFAULT NULL,
  `reviewer_name` varchar(50) DEFAULT NULL,
  `review_time` datetime DEFAULT NULL,
  `review_due_time` datetime DEFAULT NULL,
  `assigned_reviewer_id` bigint DEFAULT NULL,
  `assigned_reviewer_name` varchar(50) DEFAULT NULL,
  `review_comment` varchar(1000) DEFAULT NULL,
  `review_overdue` tinyint NOT NULL DEFAULT '0',
  `outer_photo_file_ids` varchar(500) DEFAULT NULL,
  `inner_photo_file_ids` varchar(500) DEFAULT NULL,
  `abnormal_count` int DEFAULT '0',
  `remark` varchar(1000) DEFAULT NULL,
  `deleted` tinyint DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_inspection_record_project_month` (`project_id`,`check_date`),
  KEY `idx_inspection_record_box_date` (`electric_box_id`,`check_date`),
  KEY `idx_inspection_record_status` (`status`),
  KEY `idx_inspection_record_review_assignment` (`project_id`,`status`,`assigned_reviewer_id`,`review_due_time`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='检查记录主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inspection_record`
--

LOCK TABLES `inspection_record` WRITE;
/*!40000 ALTER TABLE `inspection_record` DISABLE KEYS */;
INSERT INTO `inspection_record` VALUES (1,1,1,'ELECTRIC_BOX_DAILY','ELECTRICIAN_DAILY',NULL,'2026-07-28',1,'系统管理员','COMPLETED','NOT_REQUIRED',NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,'2','3',0,'唯一演示巡检记录，六项检查均正常。',0,'2026-07-28 10:40:56','2026-07-28 10:40:56');
/*!40000 ALTER TABLE `inspection_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `inspection_record_item`
--

DROP TABLE IF EXISTS `inspection_record_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inspection_record_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `record_id` bigint NOT NULL,
  `item_code` varchar(64) NOT NULL,
  `item_name` varchar(100) NOT NULL,
  `result` varchar(30) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `deleted` tinyint DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_record_item_record` (`record_id`),
  KEY `idx_record_item_result` (`result`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='检查项结果明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inspection_record_item`
--

LOCK TABLES `inspection_record_item` WRITE;
/*!40000 ALTER TABLE `inspection_record_item` DISABLE KEYS */;
INSERT INTO `inspection_record_item` VALUES (1,1,'APPEARANCE','内外观','NORMAL','箱体和标识完好',0,'2026-07-28 10:40:56','2026-07-28 10:40:56'),(2,1,'LEAKAGE_PROTECTOR','漏电保护器','NORMAL','试跳动作正常',0,'2026-07-28 10:40:56','2026-07-28 10:40:56'),(3,1,'FUSE','熔断','NORMAL','熔断器规格匹配',0,'2026-07-28 10:40:56','2026-07-28 10:40:56'),(4,1,'PROTECTIVE_ZERO','保护接零','NORMAL','接零连接可靠',0,'2026-07-28 10:40:56','2026-07-28 10:40:56'),(5,1,'SOCKET_220V','220V插座','NORMAL','插座无破损',0,'2026-07-28 10:40:56','2026-07-28 10:40:56'),(6,1,'SOCKET_380V','380V插座','NORMAL','插座和防护盖完好',0,'2026-07-28 10:40:56','2026-07-28 10:40:56');
/*!40000 ALTER TABLE `inspection_record_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `inspection_rectification`
--

DROP TABLE IF EXISTS `inspection_rectification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inspection_rectification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `electric_box_id` bigint NOT NULL,
  `inspection_record_id` bigint DEFAULT NULL,
  `record_item_id` bigint DEFAULT NULL,
  `box_code` varchar(64) DEFAULT NULL,
  `problem_desc` varchar(1000) NOT NULL,
  `problem_category` varchar(50) DEFAULT NULL,
  `requirement` varchar(1000) DEFAULT NULL,
  `assignee_id` bigint DEFAULT NULL,
  `assignee_name` varchar(50) DEFAULT NULL,
  `deadline` date DEFAULT NULL,
  `status` varchar(30) DEFAULT 'PENDING',
  `feedback` varchar(1000) DEFAULT NULL,
  `rectification_photo_file_ids` varchar(500) DEFAULT NULL,
  `completed_time` datetime DEFAULT NULL,
  `reviewer_id` bigint DEFAULT NULL,
  `reviewer_name` varchar(50) DEFAULT NULL,
  `review_time` datetime DEFAULT NULL,
  `review_comment` varchar(1000) DEFAULT NULL,
  `reject_count` int NOT NULL DEFAULT '0',
  `recheck_deadline` date DEFAULT NULL,
  `escalation_status` varchar(20) NOT NULL DEFAULT 'NONE',
  `escalation_time` datetime DEFAULT NULL,
  `escalation_note` varchar(1000) DEFAULT NULL,
  `close_time` datetime DEFAULT NULL,
  `deleted` tinyint DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_rectification_project_status` (`project_id`,`status`),
  KEY `idx_rectification_assignee` (`assignee_id`,`status`),
  KEY `idx_rectification_record` (`inspection_record_id`),
  KEY `idx_rectification_category` (`project_id`,`problem_category`,`status`),
  KEY `idx_rectification_escalation` (`project_id`,`status`,`deadline`,`escalation_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='整改闭环任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inspection_rectification`
--

LOCK TABLES `inspection_rectification` WRITE;
/*!40000 ALTER TABLE `inspection_rectification` DISABLE KEYS */;
/*!40000 ALTER TABLE `inspection_rectification` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `inspection_rectification_review_log`
--

DROP TABLE IF EXISTS `inspection_rectification_review_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inspection_rectification_review_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `rectification_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `electric_box_id` bigint NOT NULL,
  `inspection_record_id` bigint DEFAULT NULL,
  `action_type` varchar(40) NOT NULL,
  `from_status` varchar(30) DEFAULT NULL,
  `to_status` varchar(30) DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(50) DEFAULT NULL,
  `comment` varchar(1000) DEFAULT NULL,
  `photo_file_ids` varchar(500) DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_rectification_log_task` (`rectification_id`,`create_time`),
  KEY `idx_rectification_log_project` (`project_id`,`create_time`),
  KEY `idx_rectification_log_action` (`action_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='检查整改闭环留痕表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inspection_rectification_review_log`
--

LOCK TABLES `inspection_rectification_review_log` WRITE;
/*!40000 ALTER TABLE `inspection_rectification_review_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `inspection_rectification_review_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `inspection_review_log`
--

DROP TABLE IF EXISTS `inspection_review_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inspection_review_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `record_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `electric_box_id` bigint NOT NULL,
  `action_type` varchar(40) NOT NULL,
  `from_reviewer_id` bigint DEFAULT NULL,
  `from_reviewer_name` varchar(50) DEFAULT NULL,
  `to_reviewer_id` bigint DEFAULT NULL,
  `to_reviewer_name` varchar(50) DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(50) DEFAULT NULL,
  `comment` varchar(1000) DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_review_log_record` (`record_id`,`create_time`),
  KEY `idx_review_log_project` (`project_id`,`create_time`),
  KEY `idx_review_log_action` (`action_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='检查记录复核留痕表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inspection_review_log`
--

LOCK TABLES `inspection_review_log` WRITE;
/*!40000 ALTER TABLE `inspection_review_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `inspection_review_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `inspection_template`
--

DROP TABLE IF EXISTS `inspection_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inspection_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `template_code` varchar(64) NOT NULL,
  `template_name` varchar(100) NOT NULL,
  `frequency` varchar(20) DEFAULT 'DAILY',
  `status` varchar(20) DEFAULT 'ACTIVE',
  `remark` varchar(500) DEFAULT NULL,
  `deleted` tinyint DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inspection_template_code` (`template_code`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='检查模板表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inspection_template`
--

LOCK TABLES `inspection_template` WRITE;
/*!40000 ALTER TABLE `inspection_template` DISABLE KEYS */;
INSERT INTO `inspection_template` VALUES (1,'ELECTRIC_BOX_DAILY','电箱检查记录表','DAILY','ACTIVE','小程序首个现场检查模板',0,'2026-07-27 19:20:42','2026-07-27 19:20:42');
/*!40000 ALTER TABLE `inspection_template` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `inspection_template_item`
--

DROP TABLE IF EXISTS `inspection_template_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inspection_template_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `template_id` bigint NOT NULL,
  `template_code` varchar(64) NOT NULL,
  `item_code` varchar(64) NOT NULL,
  `item_name` varchar(100) NOT NULL,
  `input_type` varchar(30) DEFAULT 'NORMAL_ABNORMAL',
  `required` tinyint DEFAULT '1',
  `sort_order` int DEFAULT '0',
  `abnormal_requirement` varchar(300) DEFAULT NULL,
  `deleted` tinyint DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_item_code` (`template_code`,`item_code`,`deleted`),
  KEY `idx_template_item_template` (`template_id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='检查模板项表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inspection_template_item`
--

LOCK TABLES `inspection_template_item` WRITE;
/*!40000 ALTER TABLE `inspection_template_item` DISABLE KEYS */;
INSERT INTO `inspection_template_item` VALUES (1,1,'ELECTRIC_BOX_DAILY','APPEARANCE','内外观','NORMAL_ABNORMAL',1,1,'恢复箱门闭合并清理周边杂物',0,'2026-07-27 19:20:42','2026-07-27 19:20:42'),(2,1,'ELECTRIC_BOX_DAILY','LEAKAGE_PROTECTOR','漏电保护器','NORMAL_ABNORMAL',1,2,'检查漏保动作状态并更换异常部件',0,'2026-07-27 19:20:42','2026-07-27 19:20:42'),(3,1,'ELECTRIC_BOX_DAILY','FUSE','熔断/开关','NORMAL_ABNORMAL',1,3,'恢复规范熔断和开关配置',0,'2026-07-27 19:20:42','2026-07-27 19:20:42'),(4,1,'ELECTRIC_BOX_DAILY','PROTECTIVE_ZERO','保护接零','NORMAL_ABNORMAL',1,4,'补齐保护接零并确认连接牢固',0,'2026-07-27 19:20:42','2026-07-27 19:20:42'),(5,1,'ELECTRIC_BOX_DAILY','SOCKET_220','220V插座','NORMAL_ABNORMAL',1,5,'排查220V插座和临时用电线路',0,'2026-07-27 19:20:42','2026-07-27 19:20:42'),(6,1,'ELECTRIC_BOX_DAILY','SOCKET_380','380V插座','NORMAL_ABNORMAL',1,6,'排查380V插座和临时用电线路',0,'2026-07-27 19:20:42','2026-07-27 19:20:42');
/*!40000 ALTER TABLE `inspection_template_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `person_certificate`
--

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

--
-- Dumping data for table `person_certificate`
--

LOCK TABLES `person_certificate` WRITE;
/*!40000 ALTER TABLE `person_certificate` DISABLE KEYS */;
/*!40000 ALTER TABLE `person_certificate` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `person_entry_exit_log`
--

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
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人员进退场流水';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `person_entry_exit_log`
--

LOCK TABLES `person_entry_exit_log` WRITE;
/*!40000 ALTER TABLE `person_entry_exit_log` DISABLE KEYS */;
INSERT INTO `person_entry_exit_log` VALUES (1,1,1,'ENTRY','2026-07-28 10:39:26',1,'系统管理员','演示人员入场','2026-07-28 10:39:26');
/*!40000 ALTER TABLE `person_entry_exit_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `project_document`
--

DROP TABLE IF EXISTS `project_document`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_document` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `folder_id` bigint NOT NULL DEFAULT '0',
  `document_no` varchar(100) DEFAULT NULL,
  `title` varchar(200) NOT NULL,
  `category` varchar(40) NOT NULL DEFAULT 'PROJECT_DATA' COMMENT '历史兼容字段，正式界面不再维护',
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `current_version_id` bigint DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_by_name` varchar(100) DEFAULT NULL,
  `remark` varchar(1000) DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `active_title` varchar(200) GENERATED ALWAYS AS ((case when (`deleted` = 0) then `title` else NULL end)) STORED,
  `active_document_no` varchar(100) GENERATED ALWAYS AS ((case when (`deleted` = 0) then `document_no` else NULL end)) STORED,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_document_active_title` (`project_id`,`folder_id`,`active_title`),
  UNIQUE KEY `uk_project_document_active_no` (`project_id`,`active_document_no`),
  KEY `idx_project_document_project` (`project_id`,`deleted`,`status`,`update_time`),
  KEY `idx_project_document_folder` (`project_id`,`folder_id`,`deleted`,`update_time`),
  KEY `idx_project_document_no` (`project_id`,`document_no`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工程资料主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `project_document`
--

LOCK TABLES `project_document` WRITE;
/*!40000 ALTER TABLE `project_document` DISABLE KEYS */;
INSERT INTO `project_document` (`id`, `project_id`, `folder_id`, `document_no`, `title`, `category`, `status`, `current_version_id`, `created_by`, `created_by_name`, `remark`, `deleted`, `create_time`, `update_time`) VALUES (1,1,1,'DEMO-DOC-001','智慧工地综合演示方案','PROJECT_DATA','ACTIVE',1,1,'系统管理员','唯一演示工程资料',0,'2026-07-28 10:40:56','2026-07-28 10:40:56');
/*!40000 ALTER TABLE `project_document` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `project_document_version`
--

DROP TABLE IF EXISTS `project_document_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_document_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `document_id` bigint NOT NULL,
  `version_no` int NOT NULL,
  `file_resource_id` bigint NOT NULL,
  `change_note` varchar(500) DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_by_name` varchar(100) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_document_version` (`document_id`,`version_no`),
  KEY `idx_document_version_file` (`file_resource_id`),
  KEY `idx_document_version_time` (`document_id`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工程资料版本';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `project_document_version`
--

LOCK TABLES `project_document_version` WRITE;
/*!40000 ALTER TABLE `project_document_version` DISABLE KEYS */;
INSERT INTO `project_document_version` VALUES (1,1,1,1,'初始演示版本',1,'系统管理员','2026-07-28 10:40:56');
/*!40000 ALTER TABLE `project_document_version` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `project_info`
--

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
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `project_info`
--

LOCK TABLES `project_info` WRITE;
/*!40000 ALTER TABLE `project_info` DISABLE KEYS */;
INSERT INTO `project_info` VALUES (1,'智慧工地综合演示项目','综合演示项目','12000','2026.07-2027.12','主体结构施工','normal','重大安全事故为零','一次验收合格','系统管理员','演示施工单位','用于 Web 与小程序共同联调的唯一演示项目，所有信息均为虚构演示内容。','2026-07-01','2027-12-31',121.507600,31.233200,'上海市','上海市','浦东新区','上海市浦东新区智慧工地演示现场','BD09',0,'2026-07-28 10:39:26','2026-07-28 10:39:26');
/*!40000 ALTER TABLE `project_info` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `project_inspection_setting`
--

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
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目电箱巡检设置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `project_inspection_setting`
--

LOCK TABLES `project_inspection_setting` WRITE;
/*!40000 ALTER TABLE `project_inspection_setting` DISABLE KEYS */;
INSERT INTO `project_inspection_setting` VALUES (1,1,'18:00:00',60,24,3,1,'2026-07-28 10:39:26','2026-07-28 10:39:26');
/*!40000 ALTER TABLE `project_inspection_setting` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `quality_issue`
--

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
  `severity` varchar(20) NOT NULL DEFAULT 'NORMAL' COMMENT '严重程度',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RECHECK/CLOSED',
  `assignee_id` bigint DEFAULT NULL COMMENT '整改负责人用户ID',
  `assignee_name` varchar(50) DEFAULT NULL COMMENT '整改负责人姓名快照',
  `deadline` date DEFAULT NULL COMMENT '整改期限',
  `rectification_description` varchar(1000) DEFAULT NULL COMMENT '整改说明',
  `rectification_photo_file_ids` varchar(1000) DEFAULT NULL COMMENT '整改照片文件ID',
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
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='质量问题闭环';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `quality_issue`
--

LOCK TABLES `quality_issue` WRITE;
/*!40000 ALTER TABLE `quality_issue` DISABLE KEYS */;
INSERT INTO `quality_issue` VALUES (1,1,'Q-20260728-456520','演示区域临边防护标识需补充','主体楼一层东侧演示区','现场演示点位缺少一处醒目标识，请在期限内补充。','NORMAL','PENDING',1,'系统管理员','2026-07-31',NULL,NULL,NULL,NULL,NULL,NULL,NULL,1,'系统管理员',0,'2026-07-28 10:40:57','2026-07-28 10:40:57');
/*!40000 ALTER TABLE `quality_issue` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `quality_issue_log`
--

DROP TABLE IF EXISTS `quality_issue_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quality_issue_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `issue_id` bigint NOT NULL COMMENT '质量问题ID',
  `project_id` bigint NOT NULL COMMENT '施工区域/项目ID',
  `action_type` varchar(30) NOT NULL COMMENT '操作类型',
  `from_status` varchar(20) DEFAULT NULL COMMENT '原状态',
  `to_status` varchar(20) DEFAULT NULL COMMENT '新状态',
  `operator_id` bigint NOT NULL COMMENT '操作人用户ID',
  `operator_name` varchar(50) DEFAULT NULL COMMENT '操作人姓名快照',
  `comment` varchar(1000) DEFAULT NULL COMMENT '操作说明',
  `photo_file_ids` varchar(1000) DEFAULT NULL COMMENT '操作照片文件ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_quality_issue_log_issue` (`issue_id`,`create_time`),
  KEY `idx_quality_issue_log_project` (`project_id`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='质量问题操作留痕';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `quality_issue_log`
--

LOCK TABLES `quality_issue_log` WRITE;
/*!40000 ALTER TABLE `quality_issue_log` DISABLE KEYS */;
INSERT INTO `quality_issue_log` VALUES (1,1,1,'CREATE',NULL,'PENDING',1,'系统管理员','现场演示点位缺少一处醒目标识，请在期限内补充。','4','2026-07-28 10:40:57');
/*!40000 ALTER TABLE `quality_issue_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `safety_education_batch`
--

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
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全教育批次表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `safety_education_batch`
--

LOCK TABLES `safety_education_batch` WRITE;
/*!40000 ALTER TABLE `safety_education_batch` DISABLE KEYS */;
INSERT INTO `safety_education_batch` VALUES (1,1,'演示人员三级安全教育','三级安全教育','2026-07-28 10:39:26','项目会议室','系统管理员','COMPLETED','单项目演示数据',2,'现场问答',NULL,0,'2026-07-28 10:39:26','2026-07-28 10:39:26');
/*!40000 ALTER TABLE `safety_education_batch` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `safety_education_person`
--

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
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全教育人员关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `safety_education_person`
--

LOCK TABLES `safety_education_person` WRITE;
/*!40000 ALTER TABLE `safety_education_person` DISABLE KEYS */;
INSERT INTO `safety_education_person` VALUES (1,1,1,'FINISHED','2026-07-28 10:39:26','2026-07-28 10:39:26');
/*!40000 ALTER TABLE `safety_education_person` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_operation_log`
--

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
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_operation_log`
--

LOCK TABLES `sys_operation_log` WRITE;
/*!40000 ALTER TABLE `sys_operation_log` DISABLE KEYS */;
INSERT INTO `sys_operation_log` VALUES (1,1,'系统管理员','DOCUMENT_UPLOAD','上传资料《智慧工地综合演示方案》V1','PROJECT_DOCUMENT_1',1,'127.0.0.1','2026-07-28 10:40:56'),(2,1,'系统管理员','FILE_UPLOAD','上传《演示电箱外观.png》','FILE_PROJECT_1',2,'127.0.0.1','2026-07-28 10:40:56'),(3,1,'系统管理员','FILE_UPLOAD','上传《演示电箱内部.png》','FILE_PROJECT_1',3,'127.0.0.1','2026-07-28 10:40:56'),(4,1,'系统管理员','FILE_UPLOAD','上传《演示质量问题.png》','FILE_PROJECT_1',4,'127.0.0.1','2026-07-28 10:40:56');
/*!40000 ALTER TABLE `sys_operation_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_role`
--

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

--
-- Dumping data for table `sys_role`
--

LOCK TABLES `sys_role` WRITE;
/*!40000 ALTER TABLE `sys_role` DISABLE KEYS */;
INSERT INTO `sys_role` VALUES (1,'平台管理员','PLATFORM_ADMIN','平台管理员，拥有所有权限',0,'2026-07-27 19:20:42','2026-07-27 19:20:42'),(2,'项目管理员','PROJECT_ADMIN','项目管理员，管理指定项目',0,'2026-07-27 19:20:42','2026-07-27 19:20:42'),(3,'安全管理员','SAFETY_ADMIN','安全管理员，负责安全管理',0,'2026-07-27 19:20:42','2026-07-27 19:20:42'),(4,'普通用户','USER','普通用户，仅查看权限',0,'2026-07-27 19:20:42','2026-07-27 19:20:42');
/*!40000 ALTER TABLE `sys_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user`
--

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
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user`
--

LOCK TABLES `sys_user` WRITE;
/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
INSERT INTO `sys_user` VALUES (1,'admin','$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH',1,'系统管理员','19900001000','admin@example.test',1,0,'2026-07-28 10:39:26','2026-07-28 10:39:26');
/*!40000 ALTER TABLE `sys_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user_project`
--

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
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户项目权限表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user_project`
--

LOCK TABLES `sys_user_project` WRITE;
/*!40000 ALTER TABLE `sys_user_project` DISABLE KEYS */;
INSERT INTO `sys_user_project` VALUES (1,1,1,'PROJECT_ADMIN',1,'ACTIVE',NULL,NULL,NULL,'2026-07-28 10:39:26','2026-07-28 10:39:26');
/*!40000 ALTER TABLE `sys_user_project` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user_role`
--

DROP TABLE IF EXISTS `sys_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user_role`
--

LOCK TABLES `sys_user_role` WRITE;
/*!40000 ALTER TABLE `sys_user_role` DISABLE KEYS */;
INSERT INTO `sys_user_role` VALUES (1,1,1,'2026-07-28 10:39:26');
/*!40000 ALTER TABLE `sys_user_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user_wechat_binding`
--

DROP TABLE IF EXISTS `sys_user_wechat_binding`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_wechat_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `app_id` varchar(80) NOT NULL,
  `openid` varchar(128) NOT NULL,
  `unionid` varchar(128) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED/UNBOUND',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `active_user_id` bigint GENERATED ALWAYS AS ((case when ((`status` = _utf8mb4'ACTIVE') and (`deleted` = 0)) then `user_id` else NULL end)) STORED,
  `bind_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `last_login_time` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wechat_binding_openid` (`app_id`,`openid`,`deleted`),
  UNIQUE KEY `uk_wechat_binding_active_user` (`app_id`,`active_user_id`),
  KEY `idx_wechat_binding_user` (`user_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户微信绑定';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user_wechat_binding`
--

LOCK TABLES `sys_user_wechat_binding` WRITE;
/*!40000 ALTER TABLE `sys_user_wechat_binding` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_user_wechat_binding` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `temporary_person`
--

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
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='临时人员表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `temporary_person`
--

LOCK TABLES `temporary_person` WRITE;
/*!40000 ALTER TABLE `temporary_person` DISABLE KEYS */;
INSERT INTO `temporary_person` VALUES (1,1,'演示人员','男','310101199001010011','19910002001','演示施工班组','电工','2026-07-28 10:39:26','EDUCATED','虚构演示人员',0,'2026-07-28 10:39:26','2026-07-28 10:39:26');
/*!40000 ALTER TABLE `temporary_person` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `video_access_log`
--

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

--
-- Dumping data for table `video_access_log`
--

LOCK TABLES `video_access_log` WRITE;
/*!40000 ALTER TABLE `video_access_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `video_access_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `video_layout_config`
--

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

--
-- Dumping data for table `video_layout_config`
--

LOCK TABLES `video_layout_config` WRITE;
/*!40000 ALTER TABLE `video_layout_config` DISABLE KEYS */;
/*!40000 ALTER TABLE `video_layout_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `wechat_access_application`
--

DROP TABLE IF EXISTS `wechat_access_application`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wechat_access_application` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `app_id` varchar(80) NOT NULL,
  `openid` varchar(128) NOT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `real_name` varchar(50) DEFAULT NULL,
  `project_id` bigint NOT NULL,
  `source_type` varchar(40) NOT NULL DEFAULT 'ELECTRIC_BOX',
  `source_id` bigint DEFAULT NULL,
  `matched_user_id` bigint DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `reviewer_id` bigint DEFAULT NULL,
  `reviewer_name` varchar(50) DEFAULT NULL,
  `review_comment` varchar(300) DEFAULT NULL,
  `review_time` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_wechat_application_project` (`project_id`,`status`,`create_time`),
  KEY `idx_wechat_application_openid` (`app_id`,`openid`,`project_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='微信注册和项目权限申请';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `wechat_access_application`
--

LOCK TABLES `wechat_access_application` WRITE;
/*!40000 ALTER TABLE `wechat_access_application` DISABLE KEYS */;
/*!40000 ALTER TABLE `wechat_access_application` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `wechat_message_log`
--

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

--
-- Dumping data for table `wechat_message_log`
--

LOCK TABLES `wechat_message_log` WRITE;
/*!40000 ALTER TABLE `wechat_message_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `wechat_message_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `wechat_subscription_state`
--

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

--
-- Dumping data for table `wechat_subscription_state`
--

LOCK TABLES `wechat_subscription_state` WRITE;
/*!40000 ALTER TABLE `wechat_subscription_state` DISABLE KEYS */;
/*!40000 ALTER TABLE `wechat_subscription_state` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping events for database 'dianxinyun'
--

--
-- Dumping routines for database 'dianxinyun'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-07-28 10:42:14
