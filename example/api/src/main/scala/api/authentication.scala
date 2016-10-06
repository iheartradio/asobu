package api

import asobu.distributed.RequestEnricherDefinition

  sealed trait ExampleEnricher extends RequestEnricherDefinition


  case object Authenticated extends ExampleEnricher
