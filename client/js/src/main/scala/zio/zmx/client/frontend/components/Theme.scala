package zio.zmx.client.frontend.components

import zio.Chunk

object Theme {
  sealed trait DaisyTheme {
    def name: String
  }

  object DaisyTheme {
    final case object Dark      extends DaisyTheme { override def name: String = "dark"      }
    final case object Halloween extends DaisyTheme { override def name: String = "halloween" }
    final case object Light     extends DaisyTheme { override def name: String = "light"     }
    final case object Emerald   extends DaisyTheme { override def name: String = "emerald"   }
    final case object CupCake   extends DaisyTheme { override def name: String = "cupcake"   }
    final case object Dracula   extends DaisyTheme { override def name: String = "dracula"   }
    final case object Bumblebee extends DaisyTheme { override def name: String = "bumblebee" }
    final case object Corporate extends DaisyTheme { override def name: String = "corporate" }
    final case object Synthwave extends DaisyTheme { override def name: String = "synthwave" }
    final case object Retro     extends DaisyTheme { override def name: String = "retro"     }
    final case object Cyberpunk extends DaisyTheme { override def name: String = "cyberpunk" }
    final case object Valentine extends DaisyTheme { override def name: String = "valentine" }
    final case object Garden    extends DaisyTheme { override def name: String = "garden"    }
    final case object Forest    extends DaisyTheme { override def name: String = "forest"    }
    final case object Aqua      extends DaisyTheme { override def name: String = "aqua"      }
    final case object Lofi      extends DaisyTheme { override def name: String = "lofi"      }
    final case object Pastel    extends DaisyTheme { override def name: String = "pastel"    }
    final case object Fantasy   extends DaisyTheme { override def name: String = "fantasy"   }
    final case object Wireframe extends DaisyTheme { override def name: String = "wireframe" }
    final case object Black     extends DaisyTheme { override def name: String = "black"     }
    final case object Luxury    extends DaisyTheme { override def name: String = "luxury"    }
    final case object Cmyk      extends DaisyTheme { override def name: String = "cmyk"      }

  }

  val allThemes: Chunk[DaisyTheme] = {
    import DaisyTheme._
    Chunk(
      Dark,
      Halloween,
      Light,
      Emerald,
      CupCake,
      Dracula,
      Bumblebee,
      Corporate,
      Synthwave,
      Retro,
      Cyberpunk,
      Valentine,
      Garden,
      Forest,
      Aqua,
      Lofi,
      Pastel,
      Fantasy,
      Wireframe,
      Black,
      Luxury,
      Cmyk
    )
  }
}
