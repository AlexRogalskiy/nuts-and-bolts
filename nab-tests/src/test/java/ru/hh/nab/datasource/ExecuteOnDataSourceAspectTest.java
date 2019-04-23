package ru.hh.nab.datasource;

import org.aspectj.lang.ProceedingJoinPoint;
import org.hibernate.Session;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.test.context.ContextConfiguration;
import ru.hh.nab.hibernate.HibernateTestConfig;
import ru.hh.nab.hibernate.transaction.DataSourceCacheMode;
import ru.hh.nab.hibernate.transaction.ExecuteOnDataSource;
import ru.hh.nab.hibernate.transaction.ExecuteOnDataSourceAspect;
import ru.hh.nab.testbase.hibernate.HibernateTestBase;

import static ru.hh.nab.datasource.DataSourceType.MASTER;
import static ru.hh.nab.hibernate.transaction.DataSourceContextUnsafe.getDataSourceKey;

import java.lang.annotation.Annotation;

@ContextConfiguration(classes = {HibernateTestConfig.class})
public class ExecuteOnDataSourceAspectTest extends HibernateTestBase {
  private ExecuteOnDataSourceAspect executeOnDataSourceAspect;
  private Session masterSession;
  private Session outerReadonlySession;

  @Before
  public void setUp() {
    executeOnDataSourceAspect = new ExecuteOnDataSourceAspect(transactionManager, sessionFactory);
    DataSourceType.registerPropertiesFor(DataSourceType.READONLY, new DataSourceType.DataSourceProperties(true));
    startTransaction();
  }

  @After
  public void tearDown() {
    rollBackTransaction();
    DataSourceType.clear();
  }

  @Test
  public void test() throws Throwable {
    assertEquals(MASTER, getDataSourceKey());
    masterSession = getCurrentSession();

    ProceedingJoinPoint pjpMock = mock(ProceedingJoinPoint.class);
    when(pjpMock.proceed()).then(invocation -> readonlyOuter());
    executeOnDataSourceAspect.executeOnSpecialDataSource(pjpMock, createExecuteOnReadonlyMock());

    assertEquals(MASTER, getDataSourceKey());
    assertEquals(masterSession, getCurrentSession());
  }

  private Object readonlyOuter() throws Throwable {
    assertEquals(DataSourceType.READONLY, getDataSourceKey());
    outerReadonlySession = getCurrentSession();
    assertNotEquals(masterSession, outerReadonlySession);

    ProceedingJoinPoint pjpMock = mock(ProceedingJoinPoint.class);
    when(pjpMock.proceed()).then(invocation -> readonlyInner());
    executeOnDataSourceAspect.executeOnSpecialDataSource(pjpMock, createExecuteOnReadonlyMock());

    assertEquals(DataSourceType.READONLY, getDataSourceKey());
    assertEquals(outerReadonlySession, getCurrentSession());

    return null;
  }

  private Object readonlyInner() {
    assertEquals(DataSourceType.READONLY, getDataSourceKey());
    assertEquals(outerReadonlySession, getCurrentSession());
    return null;
  }

  private static ExecuteOnDataSource createExecuteOnReadonlyMock() {
    return new ExecuteOnDataSource() {

      @Override
      public Class<? extends Annotation> annotationType() {
        return null;
      }

      @Override
      public String dataSourceType() {
        return DataSourceType.READONLY;
      }

      @Override
      public boolean overrideByRequestScope() {
        return false;
      }

      @Override
      public DataSourceCacheMode cacheMode() {
        return DataSourceCacheMode.NORMAL;
      }
    };
  }
}
