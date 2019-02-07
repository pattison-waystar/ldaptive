/* See LICENSE for licensing and NOTICE for copyright. */
package org.ldaptive;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.ldaptive.dns.DNSContextFactory;
import org.ldaptive.dns.DefaultDNSContextFactory;
import org.ldaptive.dns.SRVDNSResolver;
import org.ldaptive.dns.SRVRecord;

/**
 * DNS SRV connection strategy. Queries a DNS server for SRV records and uses those records to construct a list of URLs.
 * A time to live can be set to control how often the DNS server is consulted. See http://www.ietf.org/rfc/rfc2782.txt.
 *
 * @author  Middleware Services
 */
public class DnsSrvConnectionStrategy extends AbstractConnectionStrategy
{

  /** Default time to live for DNS results. */
  protected static final Duration DEFAULT_TTL = Duration.ofHours(1);

  /** DNS context factory to override initialization parameters. */
  private final DNSContextFactory dnsContextFactory;

  /** Time to live for SRV records. */
  private final Duration srvTtl;

  /** Connect to LDAP using LDAPS. */
  private final boolean useSSL;

  /** LDAP URL string used to initialize this strategy. */
  private String ldapUrls;

  /** Resolver(s) for SRV DNS records. */
  private Map<SRVDNSResolver, String> dnsResolvers;

  /** SRV records from the last DNS lookup. */
  private Set<SRVRecord> srvRecords;

  /** SRV records expiration time. */
  private Instant expirationTime;


  /** Default constructor. */
  public DnsSrvConnectionStrategy()
  {
    this(DEFAULT_TTL);
  }


  /**
   * Creates a new DNS SRV connection strategy.
   *
   * @param  ttl  time to live for SRV records
   */
  public DnsSrvConnectionStrategy(final Duration ttl)
  {
    this(null, ttl);
  }


  /**
   * Creates a new DNS SRV connection strategy.
   *
   * @param  factory  DNS context factory
   * @param  ttl  time to live for SRV records
   */
  public DnsSrvConnectionStrategy(final DNSContextFactory factory, final Duration ttl)
  {
    this(factory, ttl, false);
  }


  /**
   * Creates a new DNS SRV connection strategy.
   *
   * @param  factory  DNS context factory
   * @param  ttl  time to live for SRV records
   * @param  ssl  whether SRV records should produce LDAPS URLs
   */
  public DnsSrvConnectionStrategy(final DNSContextFactory factory, final Duration ttl, final boolean ssl)
  {
    dnsContextFactory = factory;
    srvTtl = ttl;
    useSSL = ssl;
  }


  @Override
  public boolean isInitialized()
  {
    return srvRecords != null && !srvRecords.isEmpty();
  }


  @Override
  public void initialize(final String urls)
  {
    ldapUrls = urls;
    if (urls == null) {
      if (dnsContextFactory == null) {
        dnsResolvers = Collections.singletonMap(new SRVDNSResolver(new DefaultDNSContextFactory(), useSSL), null);
      } else {
        dnsResolvers = Collections.singletonMap(new SRVDNSResolver(dnsContextFactory, useSSL), null);
      }
    } else if (urls.contains(" ")) {
      dnsResolvers = new HashMap<>();
      for (String url : urls.split(" ")) {
        final String[] dnsUrl = parseDnsUrl(url);
        if (dnsContextFactory == null) {
          dnsResolvers.put(new SRVDNSResolver(new DefaultDNSContextFactory(dnsUrl[0]), useSSL), dnsUrl[1]);
        } else {
          dnsResolvers.put(new SRVDNSResolver(dnsContextFactory, useSSL), dnsUrl[1]);
        }
      }
    } else {
      final String[] dnsUrl = parseDnsUrl(urls);
      if (dnsContextFactory == null) {
        dnsResolvers = Collections.singletonMap(
          new SRVDNSResolver(new DefaultDNSContextFactory(dnsUrl[0]), useSSL), dnsUrl[1]);
      } else {
        dnsResolvers = Collections.singletonMap(new SRVDNSResolver(dnsContextFactory, useSSL), dnsUrl[1]);
      }
    }
    srvRecords = retrieveDNSRecords();
    if (srvRecords.isEmpty()) {
      logger.error("No SRV records found using {}", dnsResolvers);
    }
    expirationTime = Instant.now().plus(srvTtl);
  }


  /**
   * Parses a DNS URL of the form dns://hostname/domain?record. Where record is the DNS record to retrieve.
   *
   * @param  url  to parse
   *
   * @return  array containing the DNS URL and the record name in that order
   */
  protected String[] parseDnsUrl(final String url)
  {
    if (!url.contains("?")) {
      return new String[] {url, null};
    }
    return url.split("\\?");
  }


  /**
   * Returns a list of URLs retrieved from DNS SRV records.
   *
   * @return  list of URLs to attempt connections to
   */
  @Override
  public synchronized List<LdapURL> apply()
  {
    if (!isInitialized()) {
      throw new IllegalStateException("Strategy is not initialized");
    }
    if (Instant.now().isAfter(expirationTime)) {
      initialize(ldapUrls);
      logger.debug("Retrieved SRV records from DNS: {}", srvRecords);
    } else {
      logger.debug("Using SRV records from internal cache: {}", srvRecords);
    }
    return srvRecords.stream().map(
      SRVRecord::getLdapURL).collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
  }


  /**
   * Invoke {@link org.ldaptive.dns.DNSResolver#resolve(String)} for each resolver until results are found.
   *
   * @return  list of LDAP URLs
   */
  protected Set<SRVRecord> retrieveDNSRecords()
  {
    for (Map.Entry<SRVDNSResolver, String> entry : dnsResolvers.entrySet()) {
      try {
        final Set<SRVRecord> records = entry.getKey().resolve(entry.getValue());
        if (records != null && !records.isEmpty()) {
          return records;
        }
      } catch (Exception e) {
        logger.error("Could not resolve SRV record {} using {}", entry.getValue(), entry.getKey(), e);
      }
    }
    return Collections.emptySet();
  }


  @Override
  public String toString()
  {
    return new StringBuilder(
      "[").append(getClass().getName()).append("@").append(hashCode()).append("::")
      .append("srvRecords=").append(srvRecords).append("]").toString();
  }
}
