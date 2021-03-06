package com.micronautics.gitStats.svn

import java.nio.file.{Path, Paths}

import com.micronautics.gitStats.AggCommit.AggCommits
import com.micronautics.gitStats.FileModification.RichIntTuple2
import com.micronautics.gitStats.{AggCommit, ConfigGitStats, FileModification}

import scala.collection.mutable

/**
  * One file commit to Subversion.
  * In practice, one Subversion commit may contain modifications to many files.
  * This class treats such commit as a set of commits for each file individually.
  *
  * @param userName User name, cannot be null or empty string.
  * @param fileModifs File modifications in this commit, cannot be null or empty.
  */
//TODO Parse also timestamp - will need it for time-window aggregations
//TODO Maybe also need workDir
case class SvnCommit(userName: String, fileModifs: Set[FileModification]) {
  require(userName != null, "User name must not be null")
  require(userName.nonEmpty, "User name must not be empty string")
  require(fileModifs != null, "File modifications cannot be null")
  require(fileModifs.nonEmpty, "File modifications cannot be empty")

  lazy val aggCommits: AggCommits =
    fileModifs.toList.map(modif => AggCommit(modif.language, modif.linesAdded, modif.linesDeleted))
}

//TODO Document all public API
object SvnCommit {

  //TODO Maybe Iterator[String] is enough instead of List[String]
  type CommitEntry = List[String]

  /**
    * Creates an iterator for commit entries by parsing lines from Subversion command output.
    *
    * @param svnLogOutputLines Lines from `svn log --diff` output.
    * @return Iterator over commit entries.
    * @throws IllegalArgumentException svn log entries is null.
    */
  def commitEntriesIterator(svnLogOutputLines: Iterator[String]): Iterator[CommitEntry] = {
    require(svnLogOutputLines != null, "svn log output must not be null")

    def readFirstCommitEntry: CommitEntry = {
      svnLogOutputLines
        .takeWhile(!isCommitDelimiter(_))
        .filter(isUseful)
        .toList
    }

    /* svn command output starts with a commit delimiter line; skip this first commit delimiter. */
    if (svnLogOutputLines.hasNext)
      svnLogOutputLines.next()

    new Iterator[CommitEntry] {
      override def hasNext: Boolean = svnLogOutputLines.hasNext
      override def next(): CommitEntry = readFirstCommitEntry
    }
  }

  private val commitDelimiterPattern = """^-{5,}$""".r

  def isCommitDelimiter(line: String): Boolean =
    commitDelimiterPattern.pattern.matcher(line).matches()

  private val commitHeadlinePattern = """^r\d+\s+\|\s+(\S+)\s+\|.+?\|.+$""".r
  private val fileIndexPattern = """^Index:\s+(\S+)$""".r
  private val lineCountsPattern = """^@@\s+\-\d+,(\d+)\s+\+\d+,(\d+)\s+@@$""".r

  def isUseful(line: String): Boolean =
    isCommitHeadline(line) || isFileIndex(line) || isLineCounts(line)

  def isCommitHeadline(line: String): Boolean =
    commitHeadlinePattern.pattern.matcher(line).matches()

  def isFileIndex(line: String): Boolean =
    fileIndexPattern.pattern.matcher(line).matches()

  def isLineCounts(line: String): Boolean =
    lineCountsPattern.pattern.matcher(line).matches()

  /**
    * Parses a commit entry from Subversion command output.
    *
    * @param commitEntry One commit entry extracted from `svn log --diff` output.
    * @param workDir Working directory
    * @return Some object with SvnCommit inside when parsing was successful, otherwise None.
    * @throws IllegalArgumentException commit entry is null.
    */
  /* TODO This simple parser implementation just takes numbers from unified diff output
  * and treats them as added/deleted lines count. Although it gives correct net changes,
  * but it overstates added/deleted line counts.
  * More accurate implementation should count lines starting with '-' and '+'
  * in the unified diff output.
  * But only net change makes sense.*/
  def parseSvnCommit(commitEntry: CommitEntry, workDir: Path)(implicit config: ConfigGitStats): Option[SvnCommit] = {
    var userNameOpt: Option[String] = None
    var fileNameOpt: Option[String] = None
    val fileModifEntries: mutable.Map[String, (Int, Int)] = mutable.Map()
    for (line <- commitEntry) {
      line match {
        case commitHeadlinePattern(userName) =>
          userNameOpt = Some(userName)
        case fileIndexPattern(fileName) =>
          fileNameOpt = Some(fileName)
          fileModifEntries += (fileName -> ((0, 0)))
        case lineCountsPattern(deleted, added) =>
          fileNameOpt.foreach { fileName =>
            fileModifEntries(fileName) = fileModifEntries(fileName) + ((added.toInt, deleted.toInt))
          }
        case _ =>
          Console.err.println(s"WARNING: Unexpected line: $line; last recognized user name: $userNameOpt, last recognized file: $fileNameOpt")
      }
    }
    userNameOpt.flatMap { userName =>
      if (fileModifEntries.isEmpty) None
      else {
        val fileModifs = fileModifEntries
          /* Do not resolve file path here:
          * - the file may not exist already
          * - resolve takes time, but we do not need file content at this time*/
          .map { case (fileName, (linesAdded, linesDeleted)) => FileModification(Paths.get(workDir.toString, fileName), linesAdded, linesDeleted) }
          .filterNot(_.isIgnoredFileType)
          .filterNot(_.isIgnoredPath)
          .filterNot(config.onlyKnown && _.isUnrecognizedLanguage)
          .toSet
        if (fileModifs.nonEmpty) Some(SvnCommit(userName, fileModifs)) else None
      }
    }
  }
}
