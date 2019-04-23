package ru.hh.nab.hibernate.transaction;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.SessionFactory;
import org.springframework.core.annotation.Order;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_NOT_SUPPORTED;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Aspect
@Order(0)
public class ExecuteOnDataSourceAspect {

  private final DataSourceContextTransactionManager transactionManager;
  private final SessionFactory sessionFactory;

  public ExecuteOnDataSourceAspect(DataSourceContextTransactionManager transactionManager, SessionFactory sessionFactory) {
    this.transactionManager = transactionManager;
    this.sessionFactory = sessionFactory;
  }

  @Around(value = "@annotation(executeOnDataSource)", argNames = "pjp,executeOnDataSource")
  public Object executeOnSpecialDataSource(final ProceedingJoinPoint pjp, final ExecuteOnDataSource executeOnDataSource) throws Throwable {
    String dataSourceName = executeOnDataSource.dataSourceType();
    if (DataSourceContextUnsafe.getDataSourceKey().equals(dataSourceName)
        && TransactionSynchronizationManager.isSynchronizationActive()) {
      return pjp.proceed();
    }
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    transactionTemplate.setPropagationBehavior(executeOnDataSource.readOnly() ? PROPAGATION_NOT_SUPPORTED : PROPAGATION_REQUIRES_NEW);
    transactionTemplate.setReadOnly(executeOnDataSource.readOnly());
    return DataSourceContextUnsafe.executeOn(dataSourceName, executeOnDataSource.overrideByRequestScope(),
        () -> transactionTemplate.execute(new ExecuteOnDataSourceTransactionCallback(pjp, sessionFactory, executeOnDataSource)));
  }
}
