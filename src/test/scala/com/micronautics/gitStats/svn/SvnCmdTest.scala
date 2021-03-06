package com.micronautics.gitStats.svn

import com.github.nscala_time.time.Imports.DateTime
import com.micronautics.gitStats.ConfigGitStats
import com.micronautics.gitStats.svn.SvnCmd._
import org.joda.time.format.DateTimeFormat
import org.scalatest.FunSuite

class SvnCmdTest extends FunSuite {

  test("parseSvnVersion - empty svn output") {
    val res = parseSvnVersion("")
    assert(res === None, "Version")
  }

  test("parseSvnVersion - garbage svn output") {
    val res = parseSvnVersion("""Some
                                |garbage""".stripMargin)
    assert(res === None, "Version")
  }

  //TODO Move string samples to sample files

  test("parseSvnVersion - output from svn available on RHEL") {
    val svnVersionOutput = """svn, version 1.7.14 (r1542130)
                             |   compiled Aug 12 2015, 15:46:21
                             |
                             |Copyright (C) 2013 The Apache Software Foundation.
                             |This software consists of contributions made by many people; see the NOTICE
                             |file for more information.
                             |Subversion is open source software, see http://subversion.apache.org/
                             |
                             |The following repository access (RA) modules are available:
                             |
                             |* ra_neon : Module for accessing a repository via WebDAV protocol using Neon.
                             |  - handles 'http' scheme
                             |  - handles 'https' scheme
                             |* ra_svn : Module for accessing a repository using the svn network protocol.
                             |  - with Cyrus SASL authentication
                             |  - handles 'svn' scheme
                             |* ra_local : Module for accessing a repository on local disk.
                             |  - handles 'file' scheme""".stripMargin
    val res = parseSvnVersion(svnVersionOutput)
    assert(res === Some("1.7.14"), "Version")
  }

  test("parseSvnVersion - output from CollabNet svn") {
    val svnVersionOutput = """svn, version 1.9.5 (r1770682)
                             |   compiled Dec  2 2016, 12:22:14 on x86_64-unknown-linux-gnu
                             |
                             |Copyright (C) 2016 The Apache Software Foundation.
                             |This software consists of contributions made by many people;
                             |see the NOTICE file for more information.
                             |Subversion is open source software, see http://subversion.apache.org/
                             |
                             |The following repository access (RA) modules are available:
                             |
                             |* ra_svn : Module for accessing a repository using the svn network protocol.
                             |  - with Cyrus SASL authentication
                             |  - handles 'svn' scheme
                             |* ra_local : Module for accessing a repository on local disk.
                             |  - handles 'file' scheme
                             |* ra_serf : Module for accessing a repository via WebDAV protocol using serf.
                             |  - using serf 1.3.8 (compiled with 1.3.8)
                             |  - handles 'http' scheme
                             |  - handles 'https' scheme
                             |
                             |The following authentication credential caches are available:
                             |
                             |* Plaintext cache in /root/.subversion
                             |* Gnome Keyring
                             |* GPG-Agent""".stripMargin
    val res = parseSvnVersion(svnVersionOutput)
    assert(res === Some("1.9.5"), "Version")
  }



  test("generateDateRangeOption - from and to dates are both set") {
    val res = generateDateRangeOption(ConfigGitStats(
      dateFrom = Some(new DateTime("2017-08-01")),
      dateTo = Some(new DateTime("2017-09-01"))
    ))
    assert(res === List("-r", "{2017-08-01}:{2017-09-01}"), "Date range option")
  }

  test("generateDateRangeOption - from date is set, to date is unset") {
    val res = generateDateRangeOption(ConfigGitStats(
      dateFrom = Some(new DateTime("2017-08-01")),
      dateTo = None
    ))
    val today = DateTime.now.withTimeAtStartOfDay
    val todayFormatted = DateTimeFormat.forPattern("yyyy-MM-dd").print(today)
    assert(res === List("-r", s"{2017-08-01}:{$todayFormatted}"), "Date range option")
  }

  test("generateDateRangeOption - from date is unset, to date is set") {
    val res = generateDateRangeOption(ConfigGitStats(
      dateFrom = None,
      dateTo = Some(new DateTime("2017-09-01"))
    ))
    assert(res === List("-r", "{1970-01-01}:{2017-09-01}"), "Date range option")
  }

  test("generateDateRangeOption - from and to dates are both unset") {
    val res = generateDateRangeOption(ConfigGitStats(
      dateFrom = None,
      dateTo = None
    ))
    val today = DateTime.now.withTimeAtStartOfDay
    val todayFormatted = DateTimeFormat.forPattern("yyyy-MM-dd").print(today)
    assert(res === List("-r", s"{1970-01-01}:{$todayFormatted}"), "Date range option")
  }
}
