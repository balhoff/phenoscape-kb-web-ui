package org.phenoscape.kb.ui

import rxscalajs.Observable
import org.phenoscape.kb.ui.Model.IRI
import org.phenoscape.kb.ui.Model.Taxon
import org.phenoscape.kb.ui.Model.Classification
import outwatch.http.Http
import rxscalajs.dom.Response

import java.net.URLEncoder
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

object KBAPI {

  val api: String = "http://kb.phenoscape.org/api"

  def taxon(iri: IRI): Observable[Taxon] = get[Taxon](s"$api/taxon?iri=${enc(iri.id)}")

  //  def taxon(iri: IRI): Observable[Taxon] = {
  //    Http.get(Observable.of(s"$api/taxon?iri=${enc(iri.id)}")).map(res => decode[Taxon](res.body)).collect { case Right(value) => value }
  //    //toCaseClass[Taxon](Http.get(Observable.of(s"$api/taxon?iri=${enc(iri.id)}")))
  //  }

  def classification(iri: IRI, definedBy: IRI): Observable[Classification] = get[Classification](s"$api/term/classification?iri=${enc(iri.id)}&definedBy=${enc(definedBy.id)}")

  //  def classification(iri: IRI, definedBy: IRI): Observable[Classification] = {
  //    Http.get(Observable.of(s"$api/term/classification?iri=${enc(iri.id)}&definedBy=${enc(definedBy.id)}")).map(res => decode[Classification](res.body)).collect { case Right(value) => value }
  //  }

  private def enc(value: String): String = value //FIXME

  private def get[T](uri: String)(implicit evidence: Decoder[T]): Observable[T] = toCaseClass[T](Http.get(Observable.of(uri)))

  private def toCaseClass[T](response: Observable[Response])(implicit evidence: Decoder[T]): Observable[T] = response.map(res => {
    val decoded = decode[T](res.body)
    decoded match {
      case Left(error) => println(error)
      case _           => ()
    }
    decoded
  }).collect { case Right(value) => value }

}