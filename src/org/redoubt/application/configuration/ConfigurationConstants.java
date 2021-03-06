package org.redoubt.application.configuration;

public class ConfigurationConstants {
    private ConfigurationConstants() {}
    
    public static final String CONFIGURATION_FILE_TRANSPORTS = "conf/transports.xml";
    public static final String CONFIGURATION_FILE_GLOBAL_CONFIGURATION = "conf/global-configuration.xml";
    public static final String CONFIGURATION_FILE_PARTIES = "conf/parties.xml";
    public static final String CONFIGURATION_FILE_REDOUBT_LOG4J = "conf/log4j.xml";
    public static final String CONFIGURATION_FILE_SHUTDOWN_LOG4J = "conf/shutdown-log4j.xml";
    
    public static final String CONFIGURATION_OPTION_WORK_FOLDER = "WorkFolder";
    public static final String CONFIGURATION_OPTION_BACKUP_FOLDER = "BackupFolder";
    public static final String CONFIGURATION_OPTION_DO_BACKUP = "DoBackup";
    public static final String CONFIGURATION_OPTION_SHUTDOWN_PORT = "ShutdownPort";
    public static final String CONFIGURATION_OPTION_KEYSTORE_FILE = "KeystoreFile";
    public static final String CONFIGURATION_OPTION_KEYSTORE_PASSWORD = "KeystorePassword";
    public static final String CONFIGURATION_OPTION_TRUSTSTORE_FILE = "TruststoreFile";
    public static final String CONFIGURATION_OPTION_TRUSTSTORE_PASSWORD = "TruststorePassword";
    public static final String CONFIGURATION_OPTION_AS2_MAX_FILE_SIZE_MB = "As2MaxFileSizeMB";
    
    public static final String SHUTDOWN_COMMAND = "shutdown";
    
    public static final String DIRECTION_INBOUND = "inbound";
    public static final String DIRECTION_OUTBOUND = "outbound";
    
    public static final String MDN_TYPE_SYNCHRONOUS = "synchronous";
    public static final String MDN_TYPE_ASYNCHRONOUS = "asynchronous";
    
    public static final long MDN_ASYNCHRONOUS_DELAY = 3 * 1000;
}
