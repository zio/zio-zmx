package zio.zmx.client.frontend.model

import zio._
import zio.json._

object Layout {

  sealed trait Dashboard[+T] { self =>

    // def ||[T1 >: T](other: Dashboard[T1]): Dashboard[T1] = self match {
    //   case Empty          => other
    //   case HGroup(elems1) =>
    //     other match {
    //       case HGroup(elems2) => HGroup(elems1 ++ elems2)
    //       case db             => HGroup(elems1 :+ db)
    //     }
    //   case el             =>
    //     other match {
    //       case HGroup(elems) => HGroup(Chunk(el) ++ elems)
    //       case db            => HGroup(Chunk(el, db))
    //     }
    // }
  }

  object Dashboard {
    case object Empty                                       extends Dashboard[Nothing]
    final case class Cell[+T](config: T)                    extends Dashboard[T]
    final case class HGroup[+T](elems: Chunk[Dashboard[T]]) extends Dashboard[T]
    final case class VGroup[+T](elems: Chunk[Dashboard[T]]) extends Dashboard[T]

    // TODO: Can we make this generic in Type T ?
    implicit val jsonEncoder: JsonEncoder[Dashboard[PanelConfig]] =
      JsonEncoder[(String, String)].contramap[Dashboard[PanelConfig]] {
        case Empty         => ("empty", "{}")
        case Cell(config)  => ("cell", config.toJson)
        case HGroup(elems) => ("HGroup", elems.toJson)
        case VGroup(elems) => ("VGroup", elems.toJson)
      }

    implicit val jsonDecoder: JsonDecoder[Dashboard[PanelConfig]] =
      JsonDecoder[(String, String)].mapOrFail[Dashboard[PanelConfig]] {
        case ("empty", _)      => Right(Empty)
        case ("cell", config)  => config.fromJson[PanelConfig].map(Cell(_))
        case ("HGroup", elems) => elems.fromJson[Chunk[Dashboard[PanelConfig]]].map(HGroup(_))
        case ("VGroup", elems) => elems.fromJson[Chunk[Dashboard[PanelConfig]]].map(VGroup(_))
        case _                 => Right(Empty)
      }

    implicit class DashboardOps[T](self: Dashboard[T]) {

      def find(f: T => Boolean): Option[T] = {

        def findInChunk(c: Chunk[Dashboard[T]], f: T => Boolean): Option[T] =
          if (c.isEmpty) None
          else c.head.find(f).orElse(findInChunk(c.tail, f))

        self match {
          case Empty         => None
          case cell: Cell[T] => if (f(cell.config)) Some(cell.config) else None
          case HGroup(elems) => findInChunk(elems, f)
          case VGroup(elems) => findInChunk(elems, f)
        }
      }

      /**
       * Combine two Dashboards placing them next to each other horizontally
       */
      def ||(other: Dashboard[T]): Dashboard[T] = self match {
        case Empty          => other
        case HGroup(elems1) =>
          other match {
            case HGroup(elems2) => HGroup(elems1 ++ elems2)
            case db             => HGroup(elems1 :+ db)
          }
        case el             =>
          other match {
            case HGroup(elems) => HGroup(Chunk(el) ++ elems)
            case db            => HGroup(Chunk(el, db))
          }
      }

      /**
       * Combine two Dashboards placing them on top of each other
       */
      def ^^(other: Dashboard[T]): Dashboard[T] = self match {
        case Empty          => other
        case VGroup(elems1) =>
          other match {
            case VGroup(elems2) => VGroup(elems1 ++ elems2)
            case db             => VGroup(elems1 :+ db)
          }
        case el             =>
          other match {
            case VGroup(elems) => VGroup(Chunk(el) ++ elems)
            case db            => VGroup(Chunk(el, db))
          }
      }

      /**
       * Extend a Dashboard by a single column
       */
      def addColumn(cfg: T): Dashboard[T] = {
        val cell = Cell(cfg)
        self match {
          case Empty           => cell
          case cell: Cell[T]   => HGroup(Chunk(cell, cell))
          case HGroup(configs) => HGroup(configs :+ cell)
          case v: VGroup[T]    => HGroup(Chunk(v, cell))
        }
      }

      /**
       * Extend a Dashboard with a single row
       */
      def addRow(cfg: T): Dashboard[T] = {
        val cell = Cell(cfg)
        self match {
          case Empty         => cell
          case c: Cell[T]    => VGroup(Chunk(c, cell))
          case h: HGroup[T]  => VGroup(Chunk(h, cell))
          case VGroup(elems) => VGroup(elems :+ cell)
        }
      }

      // A transformation transforms a cell into a Dashboard[T]
      // Closing a panel is equivalent to transforming the panel into the empty dashboard
      // Splitting a Panel would create a VGroup or a HGroup
      // After the cell is transformed we need to run optimize to clean up the Layout
      def transform(f: PartialFunction[Cell[T], Dashboard[T]]): Dashboard[T] =
        (self match {
          case Empty         => Empty
          case c: Cell[T]    => f.lift(c).getOrElse(c)
          case HGroup(elems) => HGroup(elems.map(_.transform(f)))
          case VGroup(elems) => VGroup(elems.map(_.transform(f)))
        }).optimize

      def optimize: Dashboard[T] = {

        def group(elems: Chunk[Dashboard[T]], isHorizontal: Boolean): Dashboard[T] =
          elems.filter(_ != Empty) match {
            case Chunk()  => Empty
            case Chunk(e) => e
            case o        =>
              // Within each group we combine the chunks that are "flowing" in the direction
              // so that a HGroup(HGroup(a), HGroup(b)) would become a HGroup(a::b)
              val combined: Chunk[Dashboard[T]] =
                o.foldLeft[Chunk[Chunk[Dashboard[T]]]](Chunk.empty) { case (cur, d) =>
                  d match {
                    case HGroup(elems) if isHorizontal  => cur :+ elems
                    case VGroup(elems) if !isHorizontal => cur :+ elems
                    case o                              => cur :+ Chunk(o)
                  }
                }.flatten
              if (isHorizontal) HGroup(combined) else VGroup(combined)
          }

        self match {
          case Empty         => Empty
          case c: Cell[T]    => c
          case HGroup(elems) =>
            group(elems.map(_.optimize), isHorizontal = true)
          case VGroup(elems) =>
            group(elems.map(_.optimize), isHorizontal = false)
        }
      }
    }
  }
}
