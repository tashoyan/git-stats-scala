import com.micronautics.gitStats._
import org.joda.time.{DateTime, Days}

object GitStats extends App with GitStatsOptionParsing {
  parser.parse(args, ConfigGitStats()) match {
    case Some(config) => new AllRepos()(config).process()

    case None => // arguments are bad, error message will have been displayed
  }
}

/** Walk through all repos and process them */
protected class AllRepos()(implicit config: ConfigGitStats) {

  /** Generates text output on stdout */
  def process(): Unit = {
    val repos: List[Repo] =
      for {
        file <- gitProjectsUnder(config.directory)
      } yield new Repo(file)

    /** Each [[Commit]] returned is actually a summary of related `Commit`s */
    def commitsByLanguageFor(repo: Repo): Commits = {
      val repoCommits: Commits = try {
        repo.commitsByLanguage
      } catch {
        case e: Throwable =>
          Console.err.println(s"${ e.getClass.getSimpleName }, ignoring git repo at ${ repo.dir }")
          Commits(Nil)
      }
      repoCommits
    }

    val commitsByLanguageByRepo: List[(Repo, Commits)] = repos.zip(repos.map(commitsByLanguageFor))

    report(commitsByLanguageByRepo)
    config.excelWorkbook.foreach(_.save())
    ()
  }

  private def report(commitsByLanguageByRepo: List[(Repo, Commits)]) = {
    val between: String = (
      for {
        from <- config.dateFrom
        to   =  config.dateTo.getOrElse(DateTime.now.withTimeAtStartOfDay)
      } yield {
        val days = Days.daysBetween(from, to).getDays
        s"for the $days days "
      }
    ).getOrElse("")

    val dateRange = s"${ config.fromFormatted.map(x => s"from $x").mkString } ${ config.toFormatted.map(x => s"to $x").mkString }".trim
    val dateStr = between + (if (dateRange.nonEmpty) dateRange else "for all time")

    println(s"Summary of commits in ${ commitsByLanguageByRepo.size } project${ if (commitsByLanguageByRepo.size>1) "s" else "" } $dateStr")

    if (config.subtotals) commitsByLanguageByRepo.foreach {
      case (repo, commits) =>
        if (commits.value.nonEmpty)
          if (config.excelWorkbook.isDefined)
            config.excelWorkbook.foreach(_.addSheet(title=repo.dir.getAbsolutePath, total=Commit.zero, contents=commits.value))
          else
            println(commits.asAsciiTable(title = repo.dir.getAbsolutePath))
    }

    if (commitsByLanguageByRepo.size > 1 || !config.subtotals) {
      val grandTotal: Commits =
        Commits(Nil)
          .combine(commitsByLanguageByRepo.map(_._2))

      if (grandTotal.value.isEmpty) s"No activity across ${ commitsByLanguageByRepo.size } projects." else {
        val projects = if (commitsByLanguageByRepo.size > 1) s" (lines changed across ${ commitsByLanguageByRepo.size } projects$between)" else ""

        if (config.excelWorkbook.isDefined)
          config.excelWorkbook.foreach(_.addSheet(title = s"Subtotals By Language$projects", Commit.zero, contents = grandTotal.value))
        else
          println(grandTotal.asAsciiTable(title = s"Subtotals By Language$projects"))
      }
    }
  }
}
