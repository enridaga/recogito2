package models.annotation.workshop

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

object TransactionOperation {
  
  def op(operation: () => Future[Boolean], rollback: () => Future[Unit]) =
    (operation, rollback)
  
  def transaction(operations: (() => Future[Boolean], () => Future[Unit])*)(implicit context: ExecutionContext) = {
    operations.zipWithIndex.foldLeft(Future.successful(true)) { case (fSuccess, (operation, idx)) => {
      fSuccess.flatMap { success => 
        if (success) {
          operation._1().recover { case t: Throwable =>
            false
          }
        } else {
          val takeIdx = if (idx > 1) idx - 1 else 0
          Future.sequence(operations.take(takeIdx).map(_._2())).map(_ => false)
        }
      }
    }}
  }
    
}
