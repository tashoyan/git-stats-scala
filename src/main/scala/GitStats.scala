import com.micronautics.gitStats._

object GitStats extends App with GitStatsOptionParsing {
  parser.parse(args, ConfigGitStats()) match {
    case Some(config) => new AllRepos(config).process()

    case None => // arguments are bad, error message will have been displayed
  }
}

/** Walk through all repos and process them */
protected class AllRepos(config: ConfigGitStats) {

  /** Generates text output on stdout */
  def process(): Unit = {
    val repos: List[Repo] =
      for {
        file <- gitProjectsUnder(config.directory)
      } yield new Repo(config, file)

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
    ()
  }

  private def report(commitsByLanguageByRepo: List[(Repo, Commits)]) = {
    val dateRange = s"${ config.fromFormatted.map(x => s"from $x").mkString } ${ config.toFormatted.map(x => s"to $x").mkString }".trim
    val dateStr = if (dateRange.nonEmpty) dateRange else "for all time"
    println(s"Summary of commits by ${ config.authorFullName } $dateStr")

    if (config.verbose) commitsByLanguageByRepo.foreach {
      case (repo, commits) =>
        if (commits.value.nonEmpty)
          println(commits.asAsciiTable(title = repo.dir.getAbsolutePath))
    }

    if (commitsByLanguageByRepo.size > 1) {
      val grandTotal: Commits =
        Commits(Nil)
          .combine(commitsByLanguageByRepo.map(_._2))

      if (grandTotal.value.isEmpty) "No activity." else
        println(grandTotal.asAsciiTable(
          title = "Subtotals By Language (lines changed across all projects)"
        ))
    }
  }
}
