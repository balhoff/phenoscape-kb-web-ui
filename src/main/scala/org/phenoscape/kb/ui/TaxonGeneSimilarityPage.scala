package org.phenoscape.kb.ui

import org.phenoscape.kb.ui.Model.{IRI, SimilarityMatch, Taxon, Term}
import outwatch.dom.Attributes.{style, title}
import outwatch.dom.{VNode, _}
import outwatch.redux.{Component, Store}
import rxscalajs.Observable

object TaxonGeneSimilarityPage extends Component {

  sealed trait Action

  final case class SetTaxonIRI(taxonIRIOpt: Option[IRI]) extends Action

  final case class SelectMatch(matched: SimilarityMatch) extends Action

  final case class SelectMatchesPage(page: Int) extends Action

  case class State(taxonIRI: Option[IRI], selectedMatch: Option[SimilarityMatch], selectedPage: Int = 1) extends ComponentState {

    def evolve: Action => State = {
      case SetTaxonIRI(iriOpt)     => copy(taxonIRI = iriOpt, selectedMatch = None, selectedPage = 1)
      case SelectMatch(matched)    => copy(selectedMatch = Some(matched))
      case SelectMatchesPage(page) => copy(selectedPage = page, selectedMatch = None)
    }

  }

  def apply(initState: State): VNode = view(Store.create(Seq.empty, initState))

  def view(store: Store[State, Action]): VNode = {
    val matchesPageSize = 20
    val obsTaxonIRIOpt = store.map(_.taxonIRI).distinctUntilChanged
    val obsPage = store.map(_.selectedPage).distinctUntilChanged
    val obsSubject = obsTaxonIRIOpt.flatMap(iriOpt => Util.sequence(iriOpt.map(KBAPI.taxon)))
    val obsSubjectAsTerm = obsSubject.map(_.map(t => Term(t.iri, t.label)))
    val corpusSize = KBAPI.similarityCorpusSize(IRI(Vocab.GeneSimilarityCorpus))
    val similarityMatches = for {
      (iriOpt, page) <- obsTaxonIRIOpt.combineLatest(obsPage)
      offset = (page - 1) * matchesPageSize
      simMatches <- iriOpt.map(iri => KBAPI.similarityMatches(iri, IRI(Vocab.GeneSimilarityCorpus), matchesPageSize, offset).map(_.results).startWith(Nil)).getOrElse(Observable.just(Nil))
    } yield simMatches
    val obsTotalPages = corpusSize.map(num => (num / matchesPageSize.toDouble).ceil.toInt)
    val selectedMatch = store.map(_.selectedMatch).distinctUntilChanged
    val hasMatchSelection = selectedMatch.map(_.nonEmpty)
    val queryProfileSizeOpt = obsTaxonIRIOpt.flatMap(iriOpt => Util.sequence(iriOpt.map(KBAPI.similarityProfileSize)))
    val selectedMatchProfileSizeOpt = selectedMatch.flatMap(matchedOpt => Util.sequence(matchedOpt.map(matched => KBAPI.similarityProfileSize(matched.matchProfile.iri))))
    val selectedMatchAsGene = selectedMatch.flatMap(matchedOpt => Util.sequence(matchedOpt.map(matched => KBAPI.gene(matched.matchProfile.iri))))
    val selectedMatchAnnotations = for {
      (taxonIRIOpt, matchedOpt) <- obsTaxonIRIOpt.combineLatest(selectedMatch)
      annotationsOpt <- Util.sequence(for {
        taxonIRI <- taxonIRIOpt
        matched <- matchedOpt
      } yield KBAPI.bestMatches(taxonIRI, IRI(Vocab.TaxonSimilarityCorpus), matched.matchProfile.iri, IRI(Vocab.GeneSimilarityCorpus))).startWith(None)
    } yield annotationsOpt.map(_.results).toList.flatten
    val obsTaxonLabel = obsSubject.map(_.map(_.label).getOrElse(""))

    val taxonSearch = Views.autocompleteField(KBAPI.ontologyClassSearch(_: String, Some(IRI(Vocab.VTO)), 20), obsSubjectAsTerm, (term: Term) => term.label, store.redirectMap((og: Option[Term]) => SetTaxonIRI(og.map(_.iri))), Some("any taxonomic group"), Observable.of(false))

    div(
      h3("Similar gene phenotypes"),
      p("These genes have phenotypic profiles (that result when the action of the gene is disrupted) that match most closely to the phenotypic variation within this taxonomic group."),
      div(
        cls := "panel-body",
        div(
          cls := "row",
          div(
            cls := "col-sm-4",
            h4("Taxon:"),
            taxonSearch
          )
        ),
        div(
          cls := "row",
          div(
            cls := "col-sm-12",
            h4("Gene phenotype profiles matching variation within taxon ", b(span(child <-- obsTaxonLabel))))),
        div(
          cls := "row",
          div(
            cls := "col-sm-4",
            div(hidden <-- similarityMatches.map(_.nonEmpty), i("No matches")),
            div(
              hidden <-- similarityMatches.map(_.isEmpty),
              table(
                cls := "table table-condensed",
                thead(
                  tr(
                    th("Organism"),
                    th("Gene"),
                    th(
                      Popover.simplePopover,
                      data.toggle := "popover", data.trigger := "hover", data.placement := "auto", data.container := "body",
                      data.content := "The Expect score is the number of matches one should expect to see at this level of similarity given the size of the database. The lower the Expect score, the more significant the match is.",
                      style := "white-space: nowrap;", "Expect Score\u00A0", span(cls := "glyphicon glyphicon-info-sign")),
                    th())),
                tbody(children <-- similarityMatches.map(_.map(matchRow(_, selectedMatch, store))))),
              Views.pagination(obsPage, store.redirectMap(SelectMatchesPage), obsTotalPages))),
          div(
            cls := "col-sm-8",
            div(
              cls := "well",
              hidden <-- hasMatchSelection,
              h2(cls := "text-center", i(cls := "small", "Click on any row to the left to view detailed results"))),
            div(
              cls := "panel panel-default",
              style := "margin-top: 1em;",
              hidden <-- hasMatchSelection.map(!_),
              div(
                cls := "panel-heading",
                h4(cls := "panel-title", "Match details")),
              div(
                cls := "panel-body",
                form(
                  cls := "form-horizontal kb-form-condensed",
                  div(
                    cls := "form-group",
                    label(cls := "col-sm-3 control-label", "Searched taxon:"),
                    div(
                      cls := "col-sm-9",
                      p(
                        cls := "form-control-static",
                        span(child <-- obsTaxonLabel), " ",
                        small(a(
                          href := "#", //FIXME link to phenotype profile
                          span(child <-- queryProfileSizeOpt.map(_.getOrElse(""))),
                          " phenotypes"))))),
                  div(
                    cls := "form-group",
                    label(cls := "col-sm-3 control-label", "Matched gene:"),
                    div(
                      cls := "col-sm-9",
                      p(
                        cls := "form-control-static",
                        span(child <-- selectedMatchAsGene.map(_.map(_.label).getOrElse(span()))), " ",
                        small(a(
                          href := "#", //FIXME link to phenotype profile
                          span(child <-- selectedMatchProfileSizeOpt.map(_.map(_.toString).getOrElse(""))),
                          " phenotypes"))))),
                  div(
                    cls := "form-group",
                    label(cls := "col-sm-3 control-label", "Overall similarity:"),
                    div(
                      cls := "col-sm-9",
                      p(
                        cls := "form-control-static",
                        span(child <-- selectedMatch.map(_.map(sm => sm.medianScore.formatted("%.2f")).getOrElse("")))))),
                  div(
                    cls := "form-group",
                    label(cls := "col-sm-3 control-label", "Expect score:"),
                    div(
                      cls := "col-sm-9",
                      p(
                        cls := "form-control-static",
                        span(child <-- selectedMatch.map(_.map(sm => formatExpect(sm.expectScore)).getOrElse("")))))))),
              table(
                cls := "table table-condensed table-striped",
                thead(
                  tr(
                    th("Taxon Phenotype"),
                    th("Gene Phenotype"),
                    th(
                      Popover.simplePopover,
                      style := "white-space: nowrap;", "Match Information Content\u00A0",
                      data.toggle := "popover", data.trigger := "hover", data.placement := "auto", data.container := "body",
                      data.content := "The Match Information Content (IC) describes the specificity of the match between two compared phenotypes. The IC is normalized such that a match with value of 0.0 subsumes all items in the search corpus, while a match with value of 1.0 is annotated to only one item.",
                      span(cls := "glyphicon glyphicon-info-sign")))),
                tbody(children <-- selectedMatchAnnotations.map(_.map(matchAnnotationsRow))))))))
    )
  }

  private def matchRow(matched: SimilarityMatch, selectedMatch: Observable[Option[Model.SimilarityMatch]], store: Store[State, Action]): VNode = {
    val geneIRI = matched.matchProfile.iri
    val obsGene = KBAPI.gene(geneIRI)
    val hover = createBoolHandler(false)
    val selected = selectedMatch.map(_.contains(matched))
    val hoverOrSelected = hover.combineLatestWith(selected)((hov, sel) => hov || sel)
    val classes = Util.observableCSS(hover.map("active" -> _).merge(selected.map("info" -> _)))
    tr(
      cls <-- classes,
      mouseenter(true) --> hover,
      mouseleave(false) --> hover,
      click(SelectMatch(matched)) --> store,
      td(span(cls := "common-taxon-group", img(src := Util.modelOrganismThumbnailURL(geneIRI)))),
      td(child <-- obsGene.map(_.label)),
      td(cls := "text-center", a(formatExpect(matched.expectScore))),
      td(cls := "match-list-arrow", span(hidden <-- hoverOrSelected.map(!_), "➠")))
  }

  private def matchAnnotationsRow(annotationMatch: Model.SimilarityAnnotationMatch): VNode = {
    val queryAnnotationTerm = KBAPI.termLabel(annotationMatch.queryAnnotation)
    val corpusAnnotationTerm = KBAPI.termLabel(annotationMatch.corpusAnnotation)
    tr(
      td(child <-- queryAnnotationTerm.map(_.label)),
      td(child <-- corpusAnnotationTerm.map(_.label)),
      td(
        cls := "text-center",
        a(
          span(hidden := true, cls := "popover-element",
            label(`for` := "mica", "Most Informative Common Ancestor"),
            p(name := "mica", cls := "form-control-static", span(title := annotationMatch.bestSubsumer.term.iri, annotationMatch.bestSubsumer.term.label)),
            label(`for` := "mica", "Information Content"),
            p(name := "mica", cls := "form-control-static", span(annotationMatch.bestSubsumer.ic.formatted("%.2f")))
          ),
          data.toggle := "popover", data.trigger := "focus", data.placement := "auto", data.container := "body", data.html := true, data.title := "Match Details",
          Popover.complexPopover,
          tabindex := 0,
          role := "button",
          annotationMatch.bestSubsumer.ic.formatted("%.2f")), "\u00A0",
        span(hidden := annotationMatch.bestSubsumer.disparity <= 0.25,
          Popover.simplePopover,
          style := "white-space: nowrap;",
          data.toggle := "popover", data.trigger := "hover", data.placement := "auto", data.container := "body",
          data.content := "This match was found to be informative among genes; however, it is relatively common among taxon annotations.",
          span(cls := "text-danger glyphicon glyphicon-flag"))))
  }

  private def formatExpect(expectScore: Double): String = if (expectScore < 0.01 && expectScore > -0.01) expectScore.formatted("%.1E") else expectScore.formatted("%.2f")

}
