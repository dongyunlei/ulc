/*
Navicat MySQL Data Transfer

Source Server         : test32
Source Server Version : 50626
Source Host           : 192.168.0.111:3332
Source Database       : itrip_ulc

Target Server Type    : MYSQL
Target Server Version : 50626
File Encoding         : 65001

Date: 2017-03-30 19:08:23
*/

SET FOREIGN_KEY_CHECKS=0;

-- ----------------------------
-- Table structure for host_bean
-- ----------------------------
DROP TABLE IF EXISTS `host_bean`;
CREATE TABLE `host_bean` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL,
  `host` varchar(150) NOT NULL,
  `port` int(11) NOT NULL,
  `user` varchar(150) NOT NULL,
  `pwd` varchar(200) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `host_port` (`host`,`port`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Records of host_bean
-- ----------------------------
INSERT INTO `host_bean` VALUES ('1', 'test34', '192.168.0.111', '22', 'aoliday', '0k3z/HhZGwUamWgLMUcNrA==');
INSERT INTO `host_bean` VALUES ('2', 'test2', '192.168.0.176', '22', 'aoliday', '0k3z/HhZGwUamWgLMUcNrA==');

-- ----------------------------
-- Table structure for keyword_rule
-- ----------------------------
DROP TABLE IF EXISTS `keyword_rule`;
CREATE TABLE `keyword_rule` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `host_id` int(11) NOT NULL,
  `file` varchar(150) NOT NULL,
  `keyword` varchar(255) NOT NULL,
  `exclude` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `host_file` (`host_id`,`file`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Records of keyword_rule
-- ----------------------------
INSERT INTO `keyword_rule` VALUES ('1', '1', 'error.log', 'Exception', '');

-- ----------------------------
-- Table structure for monitor_file
-- ----------------------------
DROP TABLE IF EXISTS `monitor_file`;
CREATE TABLE `monitor_file` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `host_id` int(11) NOT NULL,
  `file` varchar(150) NOT NULL,
  `exclude_file` varchar(150) DEFAULT '',
  PRIMARY KEY (`id`),
  UNIQUE KEY `host_file` (`host_id`,`file`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Records of monitor_file
-- ----------------------------
INSERT INTO `monitor_file` VALUES ('1', '1', 'tail -n 1 -f /data/work/itrip_4/*/tomcat7/logs/*.log', 'redis.log');
INSERT INTO `monitor_file` VALUES ('2', '2', 'tail -n 1 -f /data/work/itrip_4/*/tomcat7/logs/*.log', null);
INSERT INTO `monitor_file` VALUES ('3', '2', 'tail -n 1 -f /data/work/itrip_2/*/tomcat7/logs/*.log', '');
INSERT INTO `monitor_file` VALUES ('5', '2', 'tail -n 1 -f /data/work/itrip_3/*/tomcat7/logs/*.log', '');
