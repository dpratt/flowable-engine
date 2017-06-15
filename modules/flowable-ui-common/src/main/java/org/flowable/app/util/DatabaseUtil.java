package org.flowable.app.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.lookup.JndiDataSourceLookup;
import org.apache.commons.lang3.StringUtils;

import javax.sql.DataSource;

public final class DatabaseUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseUtil.class);

  public static JdbcDataSource createDataSource(Environment env) {
    LOGGER.info("Configuring Datasource");

    String dataSourceJndiName = env.getProperty("datasource.jndi.name");
    if (StringUtils.isNotEmpty(dataSourceJndiName)) {

      LOGGER.info("Using jndi datasource '{}'", dataSourceJndiName);
      JndiDataSourceLookup dsLookup = new JndiDataSourceLookup();
      dsLookup.setResourceRef(env.getProperty("datasource.jndi.resourceRef", Boolean.class, Boolean.TRUE));
      final DataSource underlying = dsLookup.getDataSource(dataSourceJndiName);
      return new JdbcDataSource() {
        @Override
        public DataSource getDataSource() {
          return underlying;
        }

        @Override
        public void close() {
          //do nothing - we don't close JDBC data sources.
        }
      };
    } else {

      HikariConfig hikariConfig = new HikariConfig();

      String dataSourceDriver = env.getProperty("datasource.driver", "org.h2.Driver");
      hikariConfig.setDriverClassName(dataSourceDriver);

      String dataSourceUrl = env.getProperty("datasource.url", "jdbc:h2:mem:flowableidm;DB_CLOSE_DELAY=-1");
      String dataSourceUsername = env.getProperty("datasource.username", "sa");
      String dataSourcePassword = env.getProperty("datasource.password", "");

      hikariConfig.setJdbcUrl(dataSourceUrl);
      hikariConfig.setUsername(dataSourceUsername);
      hikariConfig.setPassword(dataSourcePassword);

      Integer minPoolSize = env.getProperty("datasource.min-pool-size", Integer.class);
      if (minPoolSize == null) {
        minPoolSize = 5;
      }

      Integer maxPoolSize = env.getProperty("datasource.max-pool-size", Integer.class);
      if (maxPoolSize == null) {
        maxPoolSize = 20;
      }

      Integer maxIdleTime = env.getProperty("datasource.max-idle-time", Integer.class);
      if (maxIdleTime != null) {
        hikariConfig.setIdleTimeout(maxIdleTime);
      }

      hikariConfig.setMinimumIdle(minPoolSize);
      hikariConfig.setMaximumPoolSize(maxPoolSize);

      LOGGER.info("Configuring Datasource with following properties (omitted password for security)");
      LOGGER.info("datasource driver : {}", dataSourceDriver);
      LOGGER.info("datasource url : {}", dataSourceUrl);
      LOGGER.info("datasource user name : {}", dataSourceUsername);
      LOGGER.info("Min pool size | Max pool size : {} | {} ", minPoolSize, maxPoolSize);

      final HikariDataSource underlying = new HikariDataSource(hikariConfig);
      return new JdbcDataSource() {
        @Override
        public DataSource getDataSource() {
          return underlying;
        }

        @Override
        public void close() {
          LOGGER.info("Shutting down database connection pool.");
          underlying.close();
        }
      };


    }

  }

}
