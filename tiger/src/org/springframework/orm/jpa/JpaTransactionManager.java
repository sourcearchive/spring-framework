/*
 * Copyright 2002-2006 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.orm.jpa;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.PersistenceException;
import javax.persistence.RollbackException;
import javax.sql.DataSource;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.datasource.ConnectionHandle;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.JdbcTransactionObjectSupport;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;

/**
 * PlatformTransactionManager implementation for a single JPA EntityManagerFactory.
 * Binds a JPA EntityManager from the specified factory to the thread, potentially
 * allowing for one thread EntityManager per factory. SharedEntityManagerCreator
 * and JpaTemplate are aware of thread-bound entity managers and participate in such
 * transactions automatically. Using either is required for JPA access code supporting
 * this transaction management mechanism.
 *
 * <p>This implementation is appropriate for applications that solely use JPA for
 * transactional data access. JTA (usually through JtaTransactionManager) is necessary for
 * accessing multiple transactional resources. Note that you need to configure your JPA
 * provider accordingly to make it participate in JTA transactions.
 *
 * <p>With a JpaDialect specified, this implementation also supports direct DataSource
 * access within a transaction (i.e. plain JDBC code working with the same DataSource).
 * This allows for mixing services that access JPA (including transactional caching)
 * and services that use plain JDBC (without being aware of JPA)!
 * Application code needs to stick to the same simple Connection lookup pattern as
 * with DataSourceTransactionManager (i.e. <code>DataSourceUtils.getConnection</code>
 * or going through a TransactionAwareDataSourceProxy).
 *
 * <p>Note that to be able to register a DataSource's Connection for plain JDBC code,
 * this instance needs to be aware of the DataSource (see "dataSource" property).
 * The given DataSource should obviously match the one used by the given
 * EntityManagerFactory. This transaction manager will autodetect the DataSource
 * used as known connection factory of the EntityManagerFactory, so you usually
 * don't need to explicitly specify the "dataSource" property.
 *
 * <p>On JDBC 3.0, this transaction manager supports nested transactions via JDBC
 * 3.0 Savepoints. The "nestedTransactionAllowed" flag defaults to "false", though,
 * as nested transactions will just apply to the JDBC Connection, not to the JPA
 * EntityManager and its cached objects. You can manually set the flag to "true"
 * if you want to use nested transactions for JDBC access code that participates
 * in JPA transactions (provided that your JDBC driver supports Savepoints).
 * <i>Note that JPA itself does not support nested transactions! Hence,
 * do not expect JPA access code to participate in a nested transaction.</i>
 *
 * @author Juergen Hoeller
 * @since 2.0
 * @see #setEntityManagerFactory
 * @see #setDataSource
 * @see LocalEntityManagerFactoryBean
 * @see JpaTemplate#execute
 * @see org.springframework.orm.jpa.support.SharedEntityManagerBean
 * @see org.springframework.jdbc.datasource.DataSourceUtils#getConnection
 * @see org.springframework.jdbc.datasource.DataSourceUtils#releaseConnection
 * @see org.springframework.jdbc.core.JdbcTemplate
 * @see org.springframework.jdbc.datasource.DataSourceTransactionManager
 * @see org.springframework.transaction.jta.JtaTransactionManager
 */
public class JpaTransactionManager extends AbstractPlatformTransactionManager implements InitializingBean {

	private EntityManagerFactory entityManagerFactory;

	private final Map jpaPropertyMap = new HashMap();

	private DataSource dataSource;

	private JpaDialect jpaDialect = new DefaultJpaDialect();


	/**
	 * Create a new JpaTransactionManager instance.
	 * A EntityManagerFactory has to be set to be able to use it.
	 * @see #setEntityManagerFactory
	 */
	public JpaTransactionManager() {
	}

	/**
	 * Create a new JpaTransactionManager instance.
	 * @param emf EntityManagerFactory to manage transactions for
	 */
	public JpaTransactionManager(EntityManagerFactory emf) {
		this.entityManagerFactory = emf;
		afterPropertiesSet();
	}

	/**
	 * Set the EntityManagerFactory that this instance should manage transactions for.
	 */
	public void setEntityManagerFactory(EntityManagerFactory emf) {
		this.entityManagerFactory = emf;
	}

	/**
	 * Return the EntityManagerFactory that this instance should manage transactions for.
	 */
	public EntityManagerFactory getEntityManagerFactory() {
		return entityManagerFactory;
	}

	/**
	 * Specify JPA properties, to be passed into
	 * <code>EntityManagerFactory.createEntityManager(Map)</code> (if any).
	 * <p>Can be populated with a String "value" (parsed via PropertiesEditor)
	 * or a "props" element in XML bean definitions.
	 * @see javax.persistence.EntityManagerFactory#createEntityManager(java.util.Map)
	 */
	public void setJpaProperties(Properties jpaProperties) {
		CollectionUtils.mergePropertiesIntoMap(jpaProperties, this.jpaPropertyMap);
	}

	/**
	 * Specify JPA properties as a Map, to be passed into
	 * <code>EntityManagerFactory.createEntityManager(Map)</code> (if any).
	 * <p>Can be populated with a "map" or "props" element in XML bean definitions.
	 * @see javax.persistence.EntityManagerFactory#createEntityManager(java.util.Map)
	 */
	public void setJpaPropertyMap(Map jpaProperties) {
		if (jpaProperties != null) {
			this.jpaPropertyMap.putAll(jpaProperties);
		}
	}

	/**
	 * Allow Map access to the JPA properties to be passed to the persistence
	 * provider, with the option to add or override specific entries.
	 * <p>Useful for specifying entries directly, for example via "jpaPropertyMap[myKey]".
	 */
	public Map getJpaPropertyMap() {
		return jpaPropertyMap;
	}

	/**
	 * Set the JDBC DataSource that this instance should manage transactions for.
   * The DataSource should match the one used by the JPA EntityManagerFactory:
	 * for example, you could specify the same JNDI DataSource for both.
	 * <p>If the EntityManagerFactory uses a known DataSource as connection factory,
	 * the DataSource will be autodetected: You can still explictly specify the
	 * DataSource, but you don't need to in this case.
	 * <p>A transactional JDBC Connection for this DataSource will be provided to
	 * application code accessing this DataSource directly via DataSourceUtils
	 * or JdbcTemplate. The Connection will be taken from the JPA EntityManager.
	 * <p>Note that you need to use a JPA dialect for a specific JPA implementation
	 * to allow for exposing JPA transactions as JDBC transactions.
	 * <p>The DataSource specified here should be the target DataSource to manage
	 * transactions for, not a TransactionAwareDataSourceProxy. Only data access
	 * code may work with TransactionAwareDataSourceProxy, while the transaction
	 * manager needs to work on the underlying target DataSource. If there's
	 * nevertheless a TransactionAwareDataSourceProxy passed in, it will be
	 * unwrapped to extract its target DataSource.
	 * @see EntityManagerFactoryInfo#getDataSource()
	 * @see #setJpaDialect
	 * @see org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
	 * @see org.springframework.jdbc.datasource.DataSourceUtils
	 * @see org.springframework.jdbc.core.JdbcTemplate
	 */
	public void setDataSource(DataSource dataSource) {
		if (dataSource instanceof TransactionAwareDataSourceProxy) {
			// If we got a TransactionAwareDataSourceProxy, we need to perform transactions
			// for its underlying target DataSource, else data access code won't see
			// properly exposed transactions (i.e. transactions for the target DataSource).
			this.dataSource = ((TransactionAwareDataSourceProxy) dataSource).getTargetDataSource();
		}
		else {
			this.dataSource = dataSource;
		}
	}

	/**
	 * Return the JDBC DataSource that this instance manages transactions for.
	 */
	public DataSource getDataSource() {
		return dataSource;
	}

	/**
	 * Set the JPA dialect to use for this transaction manager.
	 * Used for vendor-specific transaction management and JDBC connection exposure.
	 * <p>If the EntityManagerFactory uses a known JpaDialect, it will be autodetected:
	 * You can still explictly specify the DataSource, but you don't need to in this case.
	 * <p>The dialect object can be used to retrieve the underlying JDBC connection
	 * and thus allows for exposing JPA transactions as JDBC transactions.
	 * @see EntityManagerFactoryInfo#getJpaDialect()
	 * @see JpaDialect#beginTransaction
	 * @see JpaDialect#getJdbcConnection
	 */
	public void setJpaDialect(JpaDialect jpaDialect) {
		this.jpaDialect = (jpaDialect != null ? jpaDialect : new DefaultJpaDialect());
	}

	/**
	 * Return the JPA dialect to use for this transaction manager.
	 */
	public JpaDialect getJpaDialect() {
		return this.jpaDialect;
	}

	/**
	 * Eagerly initialize the JPA dialect, creating a default one
	 * for the specified EntityManagerFactory if none set.
	 * Auto-detect the EntityManagerFactory's DataSource, if any.
	 */
	public void afterPropertiesSet() {
		if (getEntityManagerFactory() == null) {
			throw new IllegalArgumentException("entityManagerFactory is required");
		}
		if (getEntityManagerFactory() instanceof EntityManagerFactoryInfo) {
			EntityManagerFactoryInfo emfInfo = (EntityManagerFactoryInfo) getEntityManagerFactory();
			DataSource dataSource = emfInfo.getDataSource();
			if (dataSource != null) {
				setDataSource(dataSource);
			}
			JpaDialect jpaDialect = emfInfo.getJpaDialect();
			if (jpaDialect != null) {
				setJpaDialect(jpaDialect);
			}
		}
	}


	protected Object doGetTransaction() {
		JpaTransactionObject txObject = new JpaTransactionObject();
		txObject.setSavepointAllowed(isNestedTransactionAllowed());

		if (TransactionSynchronizationManager.hasResource(getEntityManagerFactory())) {
			EntityManagerHolder emHolder = (EntityManagerHolder)
					TransactionSynchronizationManager.getResource(getEntityManagerFactory());
			if (logger.isDebugEnabled()) {
				logger.debug("Found thread-bound EntityManager [" +
						emHolder.getEntityManager() + "] for JPA transaction");
			}
			txObject.setEntityManagerHolder(emHolder, false);
			if (getDataSource() != null) {
				ConnectionHolder conHolder = (ConnectionHolder)
						TransactionSynchronizationManager.getResource(getDataSource());
				txObject.setConnectionHolder(conHolder);
			}
		}

		return txObject;
	}

	protected boolean isExistingTransaction(Object transaction) {
		return ((JpaTransactionObject) transaction).hasTransaction();
	}

	protected void doBegin(Object transaction, TransactionDefinition definition) {
		if (getDataSource() != null && TransactionSynchronizationManager.hasResource(getDataSource())) {
			throw new IllegalTransactionStateException(
					"Pre-bound JDBC Connection found - JpaTransactionManager does not support " +
					"running within DataSourceTransactionManager if told to manage the DataSource itself. " +
					"It is recommended to use a single JpaTransactionManager for all transactions " +
					"on a single DataSource, no matter whether JPA or JDBC access.");
		}

		EntityManager em = null;

		try {
			JpaTransactionObject txObject = (JpaTransactionObject) transaction;
			if (txObject.getEntityManagerHolder() == null) {
				// Create a new EntityManager with PersistenceContextType.TRANSACTION
				EntityManager newEm = createEntityManagerForTransaction();
				if (logger.isDebugEnabled()) {
					logger.debug("Opened new EntityManager [" + newEm + "] for JPA transaction");
				}
				txObject.setEntityManagerHolder(new EntityManagerHolder(newEm), true);
			}

			txObject.getEntityManagerHolder().setSynchronizedWithTransaction(true);
			em = txObject.getEntityManagerHolder().getEntityManager();

			// Delegate to JpaDialect for actual transaction begin.
			Object transactionData = getJpaDialect().beginTransaction(em, definition);
			txObject.setTransactionData(transactionData);

			// Register transaction timeout.
			if (definition.getTimeout() != TransactionDefinition.TIMEOUT_DEFAULT) {
				txObject.getEntityManagerHolder().setTimeoutInSeconds(definition.getTimeout());
			}

			// Register the JPA EntityManager's JDBC Connection for the DataSource, if set.
			if (getDataSource() != null) {
				ConnectionHandle conHandle = getJpaDialect().getJdbcConnection(em, definition.isReadOnly());
				if (conHandle != null) {
					ConnectionHolder conHolder = new ConnectionHolder(conHandle);
					if (definition.getTimeout() != TransactionDefinition.TIMEOUT_DEFAULT) {
						conHolder.setTimeoutInSeconds(definition.getTimeout());
					}
					if (logger.isDebugEnabled()) {
						logger.debug("Exposing JPA transaction as JDBC transaction [" + conHolder.getConnectionHandle() + "]");
					}
					TransactionSynchronizationManager.bindResource(getDataSource(), conHolder);
					txObject.setConnectionHolder(conHolder);
				}
				else {
					if (logger.isDebugEnabled()) {
						logger.debug("Not exposing JPA transaction [" + em + "] as JDBC transaction because JpaDialect [" +
								getJpaDialect() + "] does not support JDBC Connection retrieval");
					}
				}
			}

			// Bind the entity manager holder to the thread.
			if (txObject.isNewEntityManagerHolder()) {
				TransactionSynchronizationManager.bindResource(
						getEntityManagerFactory(), txObject.getEntityManagerHolder());
			}
		}

		catch (TransactionException ex) {
			if (em != null) {
				em.close();
			}
			throw ex;
		}
		catch (Exception ex) {
			if (em != null) {
				em.close();
			}
			throw new CannotCreateTransactionException("Could not open JPA EntityManager for transaction", ex);
		}
	}

	/**
	 * Create a JPA EntityManager to be used for a transaction.
	 * <p>The default implementation checks whether the EntityManagerFactory
	 * is a Spring proxy and unwraps it first.
	 * @see javax.persistence.EntityManagerFactory#createEntityManager()
	 * @see EntityManagerFactoryInfo#getNativeEntityManagerFactory()
	 */
	protected EntityManager createEntityManagerForTransaction() {
		EntityManagerFactory emf = getEntityManagerFactory();
		if (emf instanceof EntityManagerFactoryInfo) {
			emf = ((EntityManagerFactoryInfo) emf).getNativeEntityManagerFactory();
		}
		Map properties = getJpaPropertyMap();
		return (!CollectionUtils.isEmpty(properties) ?
				emf.createEntityManager(properties) : emf.createEntityManager());
	}

	protected Object doSuspend(Object transaction) {
		JpaTransactionObject txObject = (JpaTransactionObject) transaction;
		txObject.setEntityManagerHolder(null, false);
		EntityManagerHolder entityManagerHolder = (EntityManagerHolder)
				TransactionSynchronizationManager.unbindResource(getEntityManagerFactory());
		ConnectionHolder connectionHolder = null;
		if (getDataSource() != null) {
			connectionHolder = (ConnectionHolder) TransactionSynchronizationManager.unbindResource(getDataSource());
		}
		return new SuspendedResourcesHolder(entityManagerHolder, connectionHolder);
	}

	protected void doResume(Object transaction, Object suspendedResources) {
		SuspendedResourcesHolder resourcesHolder = (SuspendedResourcesHolder) suspendedResources;
		TransactionSynchronizationManager.bindResource(
				getEntityManagerFactory(), resourcesHolder.getEntityManagerHolder());
		if (getDataSource() != null) {
			TransactionSynchronizationManager.bindResource(getDataSource(), resourcesHolder.getConnectionHolder());
		}
	}

	/**
	 * This implementation returns "true": a JPA commit will properly handle
	 * transactions that have been marked rollback-only at a global level.
	 */
	protected boolean shouldCommitOnGlobalRollbackOnly() {
		return true;
	}

	protected void doCommit(DefaultTransactionStatus status) {
		JpaTransactionObject txObject = (JpaTransactionObject) status.getTransaction();
		if (status.isDebug()) {
			logger.debug("Committing JPA transaction on EntityManager [" +
					txObject.getEntityManagerHolder().getEntityManager() + "]");
		}
		try {
			EntityTransaction tx = txObject.getEntityManagerHolder().getEntityManager().getTransaction();
			tx.commit();
		}
		catch (RollbackException ex) {
			throw new UnexpectedRollbackException(
					"JPA transaction unexpectedly rolled back (maybe marked rollback-only after a failed operation)", ex);
		}
		catch (RuntimeException rawException) {
			// Assumably failed to flush changes to database.
			throw DataAccessUtils.translateIfNecessary(rawException, getJpaDialect());
		}
	}

	protected void doRollback(DefaultTransactionStatus status) {
		JpaTransactionObject txObject = (JpaTransactionObject) status.getTransaction();
		if (status.isDebug()) {
			logger.debug("Rolling back JPA transaction on EntityManager [" +
					txObject.getEntityManagerHolder().getEntityManager() + "]");
		}
		try {
			EntityTransaction tx = txObject.getEntityManagerHolder().getEntityManager().getTransaction();
			if (tx.isActive()) {
				tx.rollback();
			}
		}
		catch (PersistenceException ex) {
			throw new TransactionSystemException("Could not roll back JPA transaction", ex);
		}
	}

	protected void doSetRollbackOnly(DefaultTransactionStatus status) {
		JpaTransactionObject txObject = (JpaTransactionObject) status.getTransaction();
		if (status.isDebug()) {
			logger.debug("Setting JPA transaction on EntityManager [" +
					txObject.getEntityManagerHolder().getEntityManager() + "] rollback-only");
		}
		txObject.setRollbackOnly();
	}

	protected void doCleanupAfterCompletion(Object transaction) {
		JpaTransactionObject txObject = (JpaTransactionObject) transaction;

		// Remove the entity manager holder from the thread.
		if (txObject.isNewEntityManagerHolder()) {
			TransactionSynchronizationManager.unbindResource(getEntityManagerFactory());
		}
		txObject.getEntityManagerHolder().clear();

		// Remove the JDBC connection holder from the thread, if exposed.
		if (txObject.hasConnectionHolder()) {
			TransactionSynchronizationManager.unbindResource(getDataSource());
			try {
				getJpaDialect().releaseJdbcConnection(txObject.getConnectionHolder().getConnectionHandle(),
						txObject.getEntityManagerHolder().getEntityManager());
			}
			catch (Exception ex) {
				// Just log it, to keep a transaction-related exception.
				logger.error("Could not close JDBC connection after transaction", ex);
			}
		}

		getJpaDialect().cleanupTransaction(txObject.getTransactionData());

		// Remove the entity manager holder from the thread.
		if (txObject.isNewEntityManagerHolder()) {
			EntityManager em = txObject.getEntityManagerHolder().getEntityManager();
			if (logger.isDebugEnabled()) {
				logger.debug("Closing JPA EntityManager [" + em + "] after transaction");
			}
			em.close();
		}
		else {
			logger.debug("Not closing pre-bound JPA EntityManager after transaction");
		}
	}


	/**
	 * JPA transaction object, representing a EntityManagerHolder.
	 * Used as transaction object by JpaTransactionManager.
	 *
	 * <p>Derives from JdbcTransactionObjectSupport to inherit the capability
	 * to manage JDBC 3.0 Savepoints for underlying JDBC Connections.
	 *
	 * @see EntityManagerHolder
	 */
	private static class JpaTransactionObject extends JdbcTransactionObjectSupport {

		private EntityManagerHolder entityManagerHolder;

		private boolean newEntityManagerHolder;

		private Object transactionData;

		public void setEntityManagerHolder(
				EntityManagerHolder entityManagerHolder, boolean newEntityManagerHolder) {
			this.entityManagerHolder = entityManagerHolder;
			this.newEntityManagerHolder = newEntityManagerHolder;
		}

		public EntityManagerHolder getEntityManagerHolder() {
			return entityManagerHolder;
		}

		public boolean isNewEntityManagerHolder() {
			return newEntityManagerHolder;
		}

		public boolean hasTransaction() {
			return (this.entityManagerHolder != null &&
					this.entityManagerHolder.getEntityManager() != null &&
					this.entityManagerHolder.getEntityManager().getTransaction().isActive());
		}

		public void setTransactionData(Object transactionData) {
			this.transactionData = transactionData;
		}

		public Object getTransactionData() {
			return transactionData;
		}

		public void setRollbackOnly() {
			EntityTransaction tx = this.entityManagerHolder.getEntityManager().getTransaction();
			if (tx.isActive()) {
				tx.setRollbackOnly();
			}
			if (getConnectionHolder() != null) {
				getConnectionHolder().setRollbackOnly();
			}
		}

		public boolean isRollbackOnly() {
			EntityTransaction tx = this.entityManagerHolder.getEntityManager().getTransaction();
			return tx.getRollbackOnly();
		}
	}


	/**
	 * Holder for suspended resources.
	 * Used internally by doSuspend and doResume.
	 */
	private static class SuspendedResourcesHolder {

		private final EntityManagerHolder entityManagerHolder;

		private final ConnectionHolder connectionHolder;

		private SuspendedResourcesHolder(EntityManagerHolder emHolder, ConnectionHolder conHolder) {
			this.entityManagerHolder = emHolder;
			this.connectionHolder = conHolder;
		}

		private EntityManagerHolder getEntityManagerHolder() {
			return entityManagerHolder;
		}

		private ConnectionHolder getConnectionHolder() {
			return connectionHolder;
		}
	}

}
