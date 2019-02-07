/* See LICENSE for licensing and NOTICE for copyright. */
package org.ldaptive.templates;

import java.util.concurrent.Executors;
import org.ldaptive.AbstractTest;
import org.ldaptive.ConnectionConfig;
import org.ldaptive.LdapEntry;
import org.ldaptive.PooledConnectionFactory;
import org.ldaptive.SearchRequest;
import org.ldaptive.SearchResponse;
import org.ldaptive.TestUtils;
import org.ldaptive.concurrent.AggregateSearchOperation;
import org.ldaptive.pool.PoolConfig;
import org.testng.AssertJUnit;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

/**
 * Unit test for {@link SearchTemplatesExecutor}.
 *
 * @author  Middleware Services
 */
public class SearchTemplatesExecutorTest extends AbstractTest
{

  /** Entry created for ldap tests. */
  private static LdapEntry testLdapEntry;

  /** Executor to test. */
  private SearchTemplatesExecutor executor;


  /**
   * @param  ldifFile  to create.
   *
   * @throws  Exception  On test failure.
   */
  @Parameters("createEntry26")
  @BeforeClass(groups = "templates")
  public void createLdapEntry(final String ldifFile)
    throws Exception
  {
    final String ldif = TestUtils.readFileIntoString(ldifFile);
    testLdapEntry = TestUtils.convertLdifToResult(ldif).getEntry();
    super.createLdapEntry(testLdapEntry);
    // remove objectClass as active directory adds some additional ones
    testLdapEntry.removeAttribute("objectClass");
  }


  /**
   * @param  baseDn  to search on
   *
   * @throws  Exception  On test failure.
   */
  @Parameters("ldapBaseDn")
  @BeforeClass(groups = "templates")
  public void createExecutor(final String baseDn)
    throws Exception
  {
    final ConnectionConfig cc = TestUtils.readConnectionConfig(null);
    final AggregateSearchOperation search = new AggregateSearchOperation(Executors.newFixedThreadPool(10));
    search.setRequest(SearchRequest.builder().dn(baseDn).build());

    final PooledConnectionFactory cf = new PooledConnectionFactory(cc, PoolConfig.builder().min(5).max(10).build());
    cf.initialize();
    executor = new SearchTemplatesExecutor(
      search,
      new PooledConnectionFactory[] {cf, },
      new SearchTemplates(
        "(|(givenName={term1})(sn={term1}))",
        "(|(givenName={term1}*)(sn={term1}*))",
        "(|(givenName=*{term1}*)(sn=*{term1}*))",
        "(|(departmentNumber={term1})(mail={term1}))",
        "(|(departmentNumber={term1})(mail={term1}*))",
        "(|(departmentNumber={term1})(mail=*{term1}*))"),
      new SearchTemplates(
        "(&(givenName={term1})(sn={term2}))",
        "(department={term1} {term2})",
        "(department={term1}* {term2}*)",
        "(department=*{term1}* *{term2}*)"),
      new SearchTemplates(
        "(|(&(givenName={term1})(sn={term3}))" +
        "(&(givenName={term2})(sn={term3})))",
        "(|(cn={term1} {term2} {term3})(cn={term2} {term1} {term3}))",
        "(|(&(givenName={term1})(sn={term3}))" +
        "(&(givenName={term2})(sn={term3})))"));
  }


  /** @throws  Exception  On test failure. */
  @AfterClass(groups = "templates")
  public void deleteLdapEntry()
    throws Exception
  {
    super.deleteLdapEntry(testLdapEntry.getDn());
    executor.close();
  }


  /**
   * Sample executor data.
   *
   * @return  executor data
   */
  @DataProvider(name = "search-data")
  public Object[][] createTestData()
  {
    return new Object[][] {
      {"troosevelt", },
      {"0846", },
      {"theodore roosevelt", },
    };
  }


  /**
   * @param  query  to search with.
   *
   * @throws  Exception  On test failure.
   */
  @Test(groups = "templates", dataProvider = "search-data", threadPoolSize = 3, invocationCount = 50,
    timeOut = 60000)
  public void search(final String query)
    throws Exception
  {
    final Query q = new Query(query);
    q.setReturnAttributes(testLdapEntry.getAttributeNames());

    final SearchResponse sr = executor.search(q);
    AssertJUnit.assertNotNull(sr);
    AssertJUnit.assertNotNull(sr.getEntry());
    TestUtils.assertEquals(testLdapEntry, sr.getEntry());
  }
}
