package tailcall.runtime.openApi

import scala.annotation.unused

// https://swagger.io/specification/
object OpenapiModels {

  case class OpenapiDocument(
    openapi: String,
    // not used so not parsed; servers, contact, license, termsOfService
    info: OpenapiInfo,
    paths: Seq[OpenapiPath],
    components: OpenapiComponent,
  )

  case class OpenapiInfo(
    // not used so not parsed; description
    title: String,
    version: String,
  )

  case class OpenapiPath(url: String, methods: Seq[OpenapiPathMethod])

  case class OpenapiPathMethod(
    methodType: String,
    parameters: Seq[OpenapiParameter],
    responses: Seq[OpenapiResponse],
    requestBody: Option[OpenapiRequestBody],
    summary: Option[String] = None,
  )

  case class OpenapiParameter(
    name: String,
    in: String,
    required: Boolean,
    description: Option[String],
    schema: OpenapiSchemaType,
  )

  case class OpenapiResponse(code: String, description: String, content: Seq[OpenapiResponseContent])

  case class OpenapiRequestBody(required: Boolean, description: Option[String], content: Seq[OpenapiRequestBodyContent])

  case class OpenapiResponseContent(contentType: String, schema: OpenapiSchemaType)

  case class OpenapiRequestBodyContent(contentType: String, schema: OpenapiSchemaType)

  // ///////////////////////////////////////////////////////
  // decoders
  // //////////////////////////////////////////////////////

  import io.circe._
  import io.circe.generic.semiauto._

  implicit val OpenapiResponseContentDecoder: Decoder[Seq[OpenapiResponseContent]] = { (c: HCursor) =>
    case class Holder(d: OpenapiSchemaType)
    implicit val InnerDecoder: Decoder[Holder] = { (c: HCursor) =>
      c.downField("schema").as[OpenapiSchemaType].map(schema => Holder(schema))
    }
    c.as[Map[String, Holder]].map(responses => responses.map { case (ct, s) => OpenapiResponseContent(ct, s.d) }.toSeq)

  }

  implicit val OpenapiResponseDecoder: Decoder[Seq[OpenapiResponse]] = { (c: HCursor) =>
    implicit val InnerDecoder: Decoder[(String, Seq[OpenapiResponseContent])] = { (c: HCursor) =>
      c.downField("description").as[String].flatMap(description =>
        c.downField("content").as[Option[Seq[OpenapiResponseContent]]]
          .map(content => (description, content.getOrElse(Seq.empty)))
      )
    }
    c.as[Map[String, (String, Seq[OpenapiResponseContent])]]
      .map(schema => schema.map { case (code, (desc, content)) => OpenapiResponse(code, desc, content) }.toSeq)
  }

  implicit val OpenapiRequestBodyContentDecoder: Decoder[Seq[OpenapiRequestBodyContent]] = { (c: HCursor) =>
    case class Holder(d: OpenapiSchemaType)
    implicit val InnerDecoder: Decoder[Holder] = { (c: HCursor) =>
      c.downField("schema").as[OpenapiSchemaType].map(schema => Holder(schema))
    }
    c.as[Map[String, Holder]]
      .map(responses => responses.map { case (ct, s) => OpenapiRequestBodyContent(ct, s.d) }.toSeq)
  }

  implicit val OpenapiRequestBodyDecoder: Decoder[OpenapiRequestBody] = { (c: HCursor) =>
    @unused
    implicit val InnerDecoder: Decoder[(String, Seq[OpenapiRequestBodyContent])] = (c: HCursor) =>
      for {
        description <- c.downField("description").as[String]
        content     <- c.downField("content").as[Option[Seq[OpenapiRequestBodyContent]]]
      } yield (description, content.getOrElse(Seq.empty))

    for {
      requiredOpt <- c.downField("required").as[Option[Boolean]]
      description <- c.downField("description").as[Option[String]]
      content     <- c.downField("content").as[Seq[OpenapiRequestBodyContent]]
    } yield OpenapiRequestBody(required = requiredOpt.getOrElse(false), description, content)
  }

  implicit val OpenapiInfoDecoder: Decoder[OpenapiInfo]                  = deriveDecoder[OpenapiInfo]
  implicit val OpenapiParameterDecoder: Decoder[OpenapiParameter]        = (c: HCursor) =>
    for {
      name        <- c.downField("name").as[String]
      in          <- c.downField("in").as[String]
      required    <- c.downField("required").as[Option[Boolean]]
      description <- c.downField("description").as[Option[String]]
      schema      <- c.downField("schema").as[OpenapiSchemaType]
    } yield OpenapiParameter(name, in, required.getOrElse(false), description, schema)
  implicit val OpenapiPathMethodDecoder: Decoder[Seq[OpenapiPathMethod]] = { (c: HCursor) =>
    implicit val InnerDecoder
      : Decoder[(Seq[OpenapiParameter], Seq[OpenapiResponse], Option[OpenapiRequestBody], Option[String])] = {
      (c: HCursor) =>
        for {
          parameters  <- c.downField("parameters").as[Option[Seq[OpenapiParameter]]]
          responses   <- c.downField("responses").as[Seq[OpenapiResponse]]
          requestBody <- c.downField("requestBody").as[Option[OpenapiRequestBody]]
          summary     <- c.downField("summary").as[Option[String]]
        } yield (parameters.getOrElse(Seq.empty), responses, requestBody, summary)
    }
    for {
      methods <- c
        .as[Map[String, (Seq[OpenapiParameter], Seq[OpenapiResponse], Option[OpenapiRequestBody], Option[String])]]
    } yield methods.map { case (t, (p, r, rb, s)) => OpenapiPathMethod(t, p, r, rb, s) }.toSeq
  }

  implicit val OpenapiPathDecoder: Decoder[Seq[OpenapiPath]] = { (c: HCursor) =>
    c.as[Map[String, Seq[OpenapiPathMethod]]].map(paths => paths.map { case (url, ms) => OpenapiPath(url, ms) }.toSeq)
  }

  implicit val OpenapiDocumentDecoder: Decoder[OpenapiDocument] = deriveDecoder[OpenapiDocument]

}