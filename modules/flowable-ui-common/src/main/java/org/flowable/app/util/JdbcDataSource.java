package org.flowable.app.util;

import javax.sql.DataSource;

public interface JdbcDataSource extends AutoCloseable {

  DataSource getDataSource();

  void close();

}
