package com.vladmihalcea.book.hpjp.util;

import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.sourceforge.jtds.jdbcx.JtdsDataSource;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.DefaultQueryLogEntryCreator;
import org.hibernate.Interceptor;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.boot.MetadataBuilder;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.SessionFactoryBuilder;
import org.hibernate.boot.model.naming.ImplicitNamingStrategyLegacyJpaImpl;
import org.hibernate.boot.registry.BootstrapServiceRegistry;
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.dialect.PostgreSQL94Dialect;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl;
import org.hibernate.jpa.boot.internal.PersistenceUnitInfoDescriptor;
import org.hibernate.jpa.boot.spi.IntegratorProvider;
import org.hibernate.stat.SecondLevelCacheStatistics;
import org.hibernate.type.BasicType;
import org.hibernate.type.Type;
import org.hibernate.usertype.CompositeUserType;
import org.hibernate.usertype.UserType;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.After;
import org.junit.Before;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.sql.DataSource;
import java.io.Closeable;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class AbstractTest {

    protected interface DataSourceProvider {

        enum IdentifierStrategy {
            IDENTITY,
            SEQUENCE
        }

        enum Database {
            HSQLDB,
            POSTGRESQL,
            ORACLE,
            MYSQL,
            SQLSERVER
        }

        String hibernateDialect();

        DataSource dataSource();

        Class<? extends DataSource> dataSourceClassName();

        Properties dataSourceProperties();

        String url();

        String username();

        String password();

        List<IdentifierStrategy> identifierStrategies();

        Database database();
    }

    protected static class DataAccessException extends RuntimeException {
        public DataAccessException() {
        }

        public DataAccessException(String message) {
            super(message);
        }

        public DataAccessException(String message, Throwable cause) {
            super(message, cause);
        }

        public DataAccessException(Throwable cause) {
            super(cause);
        }

        public DataAccessException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }

    static {
        Thread.currentThread().setName("Alice");
    }

    protected enum LockType {
        LOCKS,
        MVLOCKS,
        MVCC
    }

    public static class InlineQueryLogEntryCreator extends DefaultQueryLogEntryCreator {
        @Override
        protected void writeParamsEntry(StringBuilder sb, ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
            sb.append("Params:[");
            for (QueryInfo queryInfo : queryInfoList) {
                boolean firstArg = true;
                for (Map<String, Object> paramMap : queryInfo.getQueryArgsList()) {

                    if(!firstArg) {
                        sb.append(", ");
                    } else {
                        firstArg = false;
                    }

                    SortedMap<String, Object> sortedParamMap = new TreeMap<>(new StringAsIntegerComparator());
                    sortedParamMap.putAll(paramMap);

                    sb.append("(");
                    boolean firstParam = true;
                    for (Map.Entry<String, Object> paramEntry : sortedParamMap.entrySet()) {
                        if(!firstParam) {
                            sb.append(", ");
                        } else {
                            firstParam = false;
                        }
                        Object parameter = paramEntry.getValue();
                        if(parameter != null && parameter.getClass().isArray()) {
                            sb.append(arrayToString(parameter));
                        } else {
                            sb.append(parameter);
                        }
                    }
                    sb.append(")");
                }
            }
            sb.append("]");
        }

        private String arrayToString(Object object) {
            if(object.getClass().isArray()) {
                if(object instanceof byte[]) {
                    return Arrays.toString((byte []) object);
                }
                if(object instanceof short[]) {
                    return Arrays.toString((short []) object);
                }
                if(object instanceof char[]) {
                    return Arrays.toString((char []) object);
                }
                if(object instanceof int[]) {
                    return Arrays.toString((int []) object);
                }
                if(object instanceof long[]) {
                    return Arrays.toString((long []) object);
                }
                if(object instanceof float[]) {
                    return Arrays.toString((float []) object);
                }
                if(object instanceof double[]) {
                    return Arrays.toString((double []) object);
                }
                if(object instanceof boolean[]) {
                    return Arrays.toString((boolean []) object);
                }
                if(object instanceof Object[]) {
                    return Arrays.toString((Object []) object);
                }
            }
            throw new UnsupportedOperationException("Arrat type not supported: " + object.getClass());
        }
    };

    public static class HsqldbDataSourceProvider implements DataSourceProvider {

        @Override
        public String hibernateDialect() {
            return "org.hibernate.dialect.HSQLDialect";
        }

        @Override
        public DataSource dataSource() {
            JDBCDataSource dataSource = new JDBCDataSource();
            dataSource.setUrl("jdbc:hsqldb:mem:test");
            dataSource.setUser("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Override
        public Class<? extends DataSource> dataSourceClassName() {
            return JDBCDataSource.class;
        }

        @Override
        public Properties dataSourceProperties() {
            Properties properties = new Properties();
            properties.setProperty("url", url());
            properties.setProperty("user", username());
            properties.setProperty("password", password());
            return properties;
        }

        @Override
        public String url() {
            return "jdbc:hsqldb:mem:test";
        }

        @Override
        public String username() {
            return "sa";
        }

        @Override
        public String password() {
            return "";
        }

        @Override
        public List<IdentifierStrategy> identifierStrategies() {
            return Arrays.asList(IdentifierStrategy.IDENTITY, IdentifierStrategy.SEQUENCE);
        }

        @Override
        public Database database() {
            return Database.HSQLDB;
        }
    }

    public static class PostgreSQLDataSourceProvider implements DataSourceProvider {

        @Override
        public String hibernateDialect() {
            return PostgreSQL94Dialect.class.getName();
        }

        @Override
        public DataSource dataSource() {
            PGSimpleDataSource dataSource = new PGSimpleDataSource();
            dataSource.setDatabaseName("high_performance_java_persistence");
            dataSource.setServerName("localhost");
            dataSource.setUser("postgres");
            dataSource.setPassword("admin");
            return dataSource;
        }

        @Override
        public Class<? extends DataSource> dataSourceClassName() {
            return PGSimpleDataSource.class;
        }

        @Override
        public Properties dataSourceProperties() {
            Properties properties = new Properties();
            properties.setProperty("databaseName", "high_performance_java_persistence");
            properties.setProperty("serverName", "localhost");
            properties.setProperty("user", username());
            properties.setProperty("password", password());
            return properties;
        }

        @Override
        public String url() {
            return null;
        }

        @Override
        public String username() {
            return "postgres";
        }

        @Override
        public String password() {
            return "admin";
        }

        @Override
        public List<IdentifierStrategy> identifierStrategies() {
            return Arrays.asList(IdentifierStrategy.SEQUENCE);
        }

        @Override
        public Database database() {
            return Database.POSTGRESQL;
        }
    }

    public static class MySQLDataSourceProvider implements DataSourceProvider {

        private boolean rewriteBatchedStatements = true;

        private boolean cachePrepStmts = false;

        private boolean useServerPrepStmts = false;

        public boolean isRewriteBatchedStatements() {
            return rewriteBatchedStatements;
        }

        public void setRewriteBatchedStatements(boolean rewriteBatchedStatements) {
            this.rewriteBatchedStatements = rewriteBatchedStatements;
        }

        public boolean isCachePrepStmts() {
            return cachePrepStmts;
        }

        public void setCachePrepStmts(boolean cachePrepStmts) {
            this.cachePrepStmts = cachePrepStmts;
        }

        public boolean isUseServerPrepStmts() {
            return useServerPrepStmts;
        }

        public void setUseServerPrepStmts(boolean useServerPrepStmts) {
            this.useServerPrepStmts = useServerPrepStmts;
        }

        @Override
        public String hibernateDialect() {
            return "org.hibernate.dialect.MySQL5Dialect";
        }

        @Override
        public DataSource dataSource() {
            MysqlDataSource dataSource = new MysqlDataSource();
            dataSource.setURL("jdbc:mysql://localhost/high_performance_java_persistence?" +
                    "rewriteBatchedStatements=" + rewriteBatchedStatements +
                    "&cachePrepStmts=" + cachePrepStmts +
                    "&useServerPrepStmts=" + useServerPrepStmts
            );
            dataSource.setUser("mysql");
            dataSource.setPassword("admin");
            return dataSource;
        }

        @Override
        public Class<? extends DataSource> dataSourceClassName() {
            return MysqlDataSource.class;
        }

        @Override
        public Properties dataSourceProperties() {
            Properties properties = new Properties();
            properties.setProperty("url", url());
            return properties;
        }

        @Override
        public String url() {
            return "jdbc:mysql://localhost/high_performance_java_persistence?user=mysql&password=admin";
        }

        @Override
        public String username() {
            return null;
        }

        @Override
        public String password() {
            return null;
        }

        @Override
        public List<IdentifierStrategy> identifierStrategies() {
            return Arrays.asList(IdentifierStrategy.IDENTITY);
        }

        @Override
        public Database database() {
            return Database.MYSQL;
        }

        @Override
        public String toString() {
            return "MySQLDataSourceProvider{" +
                    "rewriteBatchedStatements=" + rewriteBatchedStatements +
                    ", cachePrepStmts=" + cachePrepStmts +
                    ", useServerPrepStmts=" + useServerPrepStmts +
                    '}';
        }
    }

    public static class JTDSDataSourceProvider implements DataSourceProvider {
        @Override
        public String hibernateDialect() {
            return "org.hibernate.dialect.SQLServer2012Dialect";
        }

        @Override
        public DataSource dataSource() {
            JtdsDataSource dataSource = new JtdsDataSource();
            dataSource.setServerName("localhost");
            dataSource.setDatabaseName("high_performance_java_persistence");
            dataSource.setInstance("SQLEXPRESS");
            dataSource.setUser("sa");
            dataSource.setPassword("adm1n");
            return dataSource;
        }

        @Override
        public Class<? extends DataSource> dataSourceClassName() {
            return JtdsDataSource.class;
        }

        @Override
        public Properties dataSourceProperties() {
            Properties properties = new Properties();
            properties.setProperty("databaseName", "high_performance_java_persistence");
            properties.setProperty("serverName", "localhost");
            properties.setProperty("instance", "SQLEXPRESS");
            properties.setProperty("user", username());
            properties.setProperty("password", password());
            return properties;
        }

        @Override
        public String url() {
            return null;
        }

        @Override
        public String username() {
            return "sa";
        }

        @Override
        public String password() {
            return "adm1n";
        }

        @Override
        public List<IdentifierStrategy> identifierStrategies() {
            return Arrays.asList(IdentifierStrategy.IDENTITY, IdentifierStrategy.SEQUENCE);
        }

        @Override
        public Database database() {
            return Database.SQLSERVER;
        }
    }

    protected final ExecutorService executorService = Executors.newSingleThreadExecutor(r -> {
        Thread bob = new Thread(r);
        bob.setName("Bob");
        return bob;
    });

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    @FunctionalInterface
    protected interface VoidCallable extends Callable<Void> {

        void execute();

        default Void call() throws Exception {
            execute();
            return null;
        }
    }

    @FunctionalInterface
    protected interface HibernateTransactionFunction<T> extends Function<Session, T> {
        default void beforeTransactionCompletion() {

        }

        default void afterTransactionCompletion() {

        }
    }

    @FunctionalInterface
    protected interface HibernateTransactionConsumer extends Consumer<Session> {
        default void beforeTransactionCompletion() {

        }

        default void afterTransactionCompletion() {

        }
    }

    @FunctionalInterface
    protected interface JPATransactionFunction<T> extends Function<EntityManager, T> {
        default void beforeTransactionCompletion() {

        }

        default void afterTransactionCompletion() {

        }
    }

    @FunctionalInterface
    protected interface JPATransactionVoidFunction extends Consumer<EntityManager> {
        default void beforeTransactionCompletion() {

        }

        default void afterTransactionCompletion() {

        }
    }

    @FunctionalInterface
    protected interface ConnectionCallable<T> {
        T execute(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    protected interface ConnectionVoidCallable {
        void execute(Connection connection) throws SQLException;
    }

    private EntityManagerFactory emf;

    private SessionFactory sf;

    private List<Closeable> closeables = new ArrayList<>();

    @Before
    public void init() {
        if(nativeHibernateSessionFactoryBootstrap()) {
            sf = newSessionFactory();
        } else {
            emf = newEntityManagerFactory();
        }
    }

    @After
    public void destroy() {
        if(nativeHibernateSessionFactoryBootstrap()) {
            sf.close();
        } else {
            emf.close();
        }
        for(Closeable closeable : closeables) {
            try {
                closeable.close();
            } catch (IOException e) {
                LOGGER.error("Failure", e);
            }
        }
        closeables.clear();
    }

    public EntityManagerFactory entityManagerFactory() {
        return emf;
    }

    public SessionFactory sessionFactory() {
        return nativeHibernateSessionFactoryBootstrap() ? sf : entityManagerFactory().unwrap(SessionFactory.class);
    }
    protected boolean nativeHibernateSessionFactoryBootstrap() {
        return false;
    }

    protected abstract Class<?>[] entities();

    protected List<String> entityClassNames() {
        return Arrays.asList(entities()).stream().map(Class::getName).collect(Collectors.toList());
    }

    protected String[] packages() {
        return null;
    }

    protected Interceptor interceptor() {
        return null;
    }

    private SessionFactory newSessionFactory() {
        final BootstrapServiceRegistryBuilder bsrb = new BootstrapServiceRegistryBuilder();
        bsrb.enableAutoClose();

        final BootstrapServiceRegistry bsr = bsrb.build();

        final StandardServiceRegistryBuilder ssrb = new StandardServiceRegistryBuilder(bsr);
        ssrb.applySettings(properties());

        StandardServiceRegistry serviceRegistry = ssrb.build();

        final MetadataSources metadataSources = new MetadataSources(serviceRegistry);

        for (Class annotatedClass : entities()) {
            metadataSources.addAnnotatedClass(annotatedClass);
        }

        String[] packages = packages();
        if (packages != null) {
            for (String annotatedPackage : packages) {
                metadataSources.addPackage(annotatedPackage);
            }
        }

        final MetadataBuilder metadataBuilder = metadataSources.getMetadataBuilder();
        metadataBuilder.enableNewIdentifierGeneratorSupport(true);
        metadataBuilder.applyImplicitNamingStrategy(ImplicitNamingStrategyLegacyJpaImpl.INSTANCE);

        MetadataImplementor metadata = (MetadataImplementor) metadataBuilder.build();

        final SessionFactoryBuilder sfb = metadata.getSessionFactoryBuilder();

        return sfb.build();
    }

    private SessionFactory newLegacySessionFactory() {
        Properties properties = properties();
        Configuration configuration = new Configuration().addProperties(properties);
        for(Class<?> entityClass : entities()) {
            configuration.addAnnotatedClass(entityClass);
        }
        String[] packages = packages();
        if(packages != null) {
            for(String scannedPackage : packages) {
                configuration.addPackage(scannedPackage);
            }
        }
        Interceptor interceptor = interceptor();
        if(interceptor != null) {
            configuration.setInterceptor(interceptor);
        }
        final List<Type> additionalTypes = additionalTypes();
        if (additionalTypes != null) {
            configuration.registerTypeContributor((typeContributions, serviceRegistry) -> {
                additionalTypes.stream().forEach(type -> {
                    if(type instanceof BasicType) {
                        typeContributions.contributeType((BasicType) type);
                    } else if (type instanceof UserType ){
                        typeContributions.contributeType((UserType) type);
                    } else if (type instanceof CompositeUserType) {
                        typeContributions.contributeType((CompositeUserType) type);
                    }
                });
            });
        }
        return configuration.buildSessionFactory(
                new StandardServiceRegistryBuilder()
                        .applySettings(properties)
                        .build()
        );
    }

    protected EntityManagerFactory newEntityManagerFactory() {
        PersistenceUnitInfo persistenceUnitInfo = persistenceUnitInfo(getClass().getSimpleName());
        Map<String, Object> configuration = new HashMap<>();
        configuration.put(AvailableSettings.INTERCEPTOR, interceptor());
        Integrator integrator = integrator();
        if (integrator != null) {
            configuration.put("hibernate.integrator_provider", (IntegratorProvider) () -> Collections.singletonList(integrator));
        }

        EntityManagerFactoryBuilderImpl entityManagerFactoryBuilder = new EntityManagerFactoryBuilderImpl(
            new PersistenceUnitInfoDescriptor(persistenceUnitInfo), configuration
        );
        return entityManagerFactoryBuilder.build();
    }

    protected Integrator integrator() {
        return null;
    }

    protected PersistenceUnitInfoImpl persistenceUnitInfo(String name) {
        return new PersistenceUnitInfoImpl(
            name, entityClassNames(), properties()
        );
    }

    protected Properties properties() {
        Properties properties = new Properties();
        properties.put("hibernate.dialect", dataSourceProvider().hibernateDialect());
        //log settings
        properties.put("hibernate.hbm2ddl.auto", "create-drop");
        //data source settings
        DataSource dataSource = newDataSource();
        if (dataSource != null) {
            properties.put("hibernate.connection.datasource", dataSource);
        }
        properties.put("hibernate.generate_statistics", Boolean.TRUE.toString());
        //properties.put("hibernate.ejb.metamodel.population", "disabled");
        return properties;
    }

    protected DataSourceProxyType dataSourceProxyType() {
        return DataSourceProxyType.DATA_SOURCE_PROXY;
    }

    protected DataSource newDataSource() {
        DataSource dataSource =
        proxyDataSource()
            ? dataSourceProxyType().dataSource(dataSourceProvider().dataSource())
            : dataSourceProvider().dataSource();
        if(connectionPooling()) {
            HikariDataSource poolingDataSource = connectionPoolDataSource(dataSource);
            closeables.add(poolingDataSource::close);
            return poolingDataSource;
        } else {
            return dataSource;
        }
    }

    protected boolean proxyDataSource() {
        return true;
    }

    protected HikariDataSource connectionPoolDataSource(DataSource dataSource) {
        HikariConfig hikariConfig = new HikariConfig();
        int cpuCores = Runtime.getRuntime().availableProcessors();
        hikariConfig.setMaximumPoolSize(cpuCores * 4);
        hikariConfig.setDataSource(dataSource);
        return new HikariDataSource(hikariConfig);
    }

    protected boolean connectionPooling() {
        return false;
    }

    protected DataSourceProvider dataSourceProvider() {
        return new HsqldbDataSourceProvider();
    }

    protected List<Type> additionalTypes() {
        return null;
    }

    protected <T> T doInHibernate(HibernateTransactionFunction<T> callable) {
        T result = null;
        Session session = null;
        Transaction txn = null;
        try {
            session = sessionFactory().openSession();
            callable.beforeTransactionCompletion();
            txn = session.beginTransaction();

            result = callable.apply(session);
            txn.commit();
        } catch (Throwable e) {
            if ( txn != null ) txn.rollback();
            throw e;
        } finally {
            callable.afterTransactionCompletion();
            if (session != null) {
                session.close();
            }
        }
        return result;
    }

    protected void doInHibernate(HibernateTransactionConsumer callable) {
        Session session = null;
        Transaction txn = null;
        try {
            session = sessionFactory().openSession();
            callable.beforeTransactionCompletion();
            txn = session.beginTransaction();

            callable.accept(session);
            txn.commit();
        } catch (Throwable e) {
            if ( txn != null ) txn.rollback();
            throw e;
        } finally {
            callable.afterTransactionCompletion();
            if (session != null) {
                session.close();
            }
        }
    }

    protected <T> T doInJPA(JPATransactionFunction<T> function) {
        T result = null;
        EntityManager entityManager = null;
        EntityTransaction txn = null;
        try {
            entityManager = entityManagerFactory().createEntityManager();
            function.beforeTransactionCompletion();
            txn = entityManager.getTransaction();
            txn.begin();
            result = function.apply(entityManager);
            txn.commit();
        } catch (Throwable e) {
            if ( txn != null && txn.isActive()) txn.rollback();
            throw e;
        } finally {
            function.afterTransactionCompletion();
            if (entityManager != null) {
                entityManager.close();
            }
        }
        return result;
    }

    protected void doInJPA(JPATransactionVoidFunction function) {
        EntityManager entityManager = null;
        EntityTransaction txn = null;
        try {
            entityManager = entityManagerFactory().createEntityManager();
            function.beforeTransactionCompletion();
            txn = entityManager.getTransaction();
            txn.begin();
            function.accept(entityManager);
            txn.commit();
        } catch (Throwable e) {
            if ( txn != null && txn.isActive()) txn.rollback();
            throw e;
        } finally {
            function.afterTransactionCompletion();
            if (entityManager != null) {
                entityManager.close();
            }
        }
    }

    protected <T> T doInJDBC(ConnectionCallable<T> callable) {
        AtomicReference<T> result = new AtomicReference<>();
        Session session = null;
        Transaction txn = null;
        try {
            session = sessionFactory().openSession();
            txn = session.beginTransaction();
            session.doWork(connection -> {
                result.set(callable.execute(connection));
            });
            txn.commit();
        } catch (Throwable e) {
            if ( txn != null ) txn.rollback();
            throw e;
        } finally {
            if (session != null) {
                session.close();
            }
        }
        return result.get();
    }

    protected void doInJDBC(ConnectionVoidCallable callable) {
        Session session = null;
        Transaction txn = null;
        try {
            session = sessionFactory().openSession();
            txn = session.beginTransaction();
            session.doWork(callable::execute);
            txn.commit();
        } catch (Throwable e) {
            if ( txn != null ) txn.rollback();
            throw e;
        } finally {
            if (session != null) {
                session.close();
            }
        }
    }

    protected void executeSync(VoidCallable callable) {
        executeSync(Collections.singleton(callable));
    }

    protected void executeSync(Collection<VoidCallable> callables) {
        try {
            List<Future<Void>> futures = executorService.invokeAll(callables);
            for (Future<Void> future : futures) {
                future.get();
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    protected <T> void executeAsync(Runnable callable, final Runnable completionCallback) {
        final Future future = executorService.submit(callable);
        new Thread(() -> {
            while (!future.isDone()) {
                try {
                    Thread.sleep(100);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
            try {
                completionCallback.run();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).start();
    }

    protected Future<?> executeAsync(Runnable callable) {
        return executorService.submit(callable);
    }

    protected  void transact(Consumer<Connection> callback) {
        transact(callback, null);
    }

    protected  void transact(Consumer<Connection> callback, Consumer<Connection> before) {
        Connection connection = null;
        try {
            connection = newDataSource().getConnection();
            if (before != null) {
                before.accept(connection);
            }
            connection.setAutoCommit(false);
            callback.accept(connection);
            connection.commit();
        } catch (Exception e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException ex) {
                    throw new DataAccessException(e);
                }
            }
            throw (e instanceof DataAccessException ?
                    (DataAccessException) e : new DataAccessException(e));
        } finally {
            if(connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    throw new DataAccessException(e);
                }
            }
        }
    }

    protected LockType lockType() {
        return LockType.LOCKS;
    }

    protected void awaitOnLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    protected void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected <V> V sleep(int millis, Callable<V> callable) {
        V result = null;
        try {
            if (callable != null) {
                result = callable.call();
            }
            Thread.sleep(millis);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return result;
    }

    protected void awaitTermination(long timeout, TimeUnit unit) {
        try {
            executorService.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    protected String selectStringColumn(Connection connection, String sql) {
        try {
            try(Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(1);
                ResultSet resultSet = statement.executeQuery(sql);
                if(!resultSet.next()) {
                    throw new IllegalArgumentException("There was no row to be selected!");
                }
                return resultSet.getString(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    protected int update(Connection connection, String sql) {
        try {
            try(Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(1);
                return statement.executeUpdate(sql);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    protected int update(Connection connection, String sql, Object[] params) {
        try {
            try(PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(1);
                for (int i = 0; i < params.length; i++) {
                    statement.setObject(i + 1, params[i]);
                }
                return statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    protected int count(Connection connection, String sql) {
        try {
            try(Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(1);
                ResultSet resultSet = statement.executeQuery(sql);
                if(!resultSet.next()) {
                    throw new IllegalArgumentException("There was no row to be selected!");
                }
                return ((Number) resultSet.getObject(1)).intValue();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    protected void printCacheRegionStatistics(String region) {
        SecondLevelCacheStatistics statistics = sessionFactory().getStatistics().getSecondLevelCacheStatistics(region);
        LOGGER.debug("\nRegion: {},\nStatistics: {},\nEntries: {}", region, statistics, statistics.getEntries());
    }
}
