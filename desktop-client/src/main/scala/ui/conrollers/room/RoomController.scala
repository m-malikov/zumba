package ui.conrollers.room

import java.awt.image.BufferedImage

import javafx.application.Platform
import javafx.embed.swing.SwingFXUtils
import javafx.fxml.FXML
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.{CheckBox, Label}
import javafx.scene.image.ImageView
import javafx.scene.layout.{StackPane, TilePane}
import media.ImageSegment
import zio.stream._
import zio.{Fiber, Ref, Task, TaskManaged, UIO, ZIO, ZManaged}

import scala.util.Try

class RoomController(
                      val selfTile: Ref[Option[TileInfo]],
                      val tiles: Ref[Map[Byte, TileInfo]],
                    )(implicit runtime: zio.Runtime[Any]) {

  @FXML
  var tilesPane: TilePane = _

  @FXML
  var debugCheckBox: CheckBox = _

  @FXML
  var debugPanel: Node = _

  def tilesNum(size: Int): Int =
    Math.sqrt(size).ceil.toInt

  // ***** Handlers *****

  def addOne(): Unit = {
    runtime.unsafeRunAsync_(
      tiles
        .get
        .map(_.maxByOption(_._1).fold(0)(_._1))
        .flatMap(maxId => addUser((maxId + 1).toByte, s"Это тоже я ${maxId + 1}"))
    )
  }

  def removeOne(): Unit = {
    runtime.unsafeRunAsync_(
      tiles
        .get
        .map(_.maxByOption(_._1))
        .flatMap(key => ZIO.foreach(key)(pair => removeUser(pair._1)))
    )
  }

  // ***** API *****

  def makeTileNode(userName: String, imageView: ImageView): StackPane = {
    val label = new Label(userName)
    val tileNode = new StackPane(imageView, label)
    tileNode.setStyle("-fx-border-color: blue; -fx-border-width: 1 ; ")
    //    tilesPane.setPrefRows()!!!
    //    https://stackoverflow.com/questions/43369963/javafx-tile-pane-set-max-number-of-columns
    tileNode
  }

  def addUser(userId: Byte, userName: String): Task[Unit] = {
    val imageView = new ImageView()
    val bufferedImage = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB)
    val node = makeTileNode(userName, imageView)
    val tileInfo = TileInfo(userName, node, imageInfo = Some(ImageInfo(imageView, bufferedImage)))

    for {
      _ <- tiles.update(_.updated(userId, tileInfo))
      _ <- ZIO(Platform.runLater(() => addTile(node)))
    } yield ()
  }

  def removeUser(userId: Byte): Task[Unit] = {
    tiles
      .modify(tiles => (tiles.get(userId), tiles.removed(userId)))
      .collectM[Any, Throwable, Unit](new NoSuchElementException(s"User $userId is not a member of room")) {
        case Some(tileInfo) => ZIO(Platform.runLater(() => tilesPane.getChildren.remove(tileInfo.node))).unit
      }
  }

  def addTile(tileNode: StackPane): Unit = {
    tileNode.setAlignment(Pos.CENTER)
    tilesPane.getChildren.add(tileNode)
  }

  def selfVideoSink: Sink[Throwable, BufferedImage, Any, Unit] =
    Sink.foreach(image =>
      selfTile.get.flatMap {
        selfTile =>
          ZIO(
            for {
              tile <- selfTile
              imageInfo <- tile.imageInfo
              _ = imageInfo.imageView.setImage(SwingFXUtils.toFXImage(image, null))
            } yield ()
          )
      }.catchAll(error =>
        UIO(println(s"Error while consuming self video: $error"))
      )
    )

  // Process batches if too slow
  def imageSegmentsSink: Sink[Throwable, ImageSegment, Any, Unit] =
    Sink.foreach { imageSegment =>
      tiles.update { tiles =>
        for {
          tileInfo <- tiles.get(imageSegment.header.userId)
          imageInfo <- tileInfo.imageInfo
          _ = imageInfo.bufferedImage.setData(imageSegment.toRaster)
          _ = Try(imageInfo.imageView.setImage(SwingFXUtils.toFXImage(imageInfo.bufferedImage, null)))
        } yield ()
        tiles
      }
    }

  def start(
             selfVideoStream: TaskManaged[Stream[Throwable, BufferedImage]],
             videoStream: Stream[Throwable, ImageSegment],
           ): Task[Unit] = {
    val selfImageView = new ImageView
    val selfTileNode = makeTileNode("Это я", selfImageView)
    val bufferedImage = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB)
    val selfTileInfo = TileInfo("Это я", selfTileNode, Some(ImageInfo(selfImageView, bufferedImage)))

    for {
      _ <- selfTile.set(Some(selfTileInfo))
      _ <- ZIO(Platform.runLater(() => addTile(selfTileNode)))
    } yield ()
  }

}

object RoomController {
  def acquireRoomController(implicit runtime: zio.Runtime[Any]): UIO[RoomController] =
    for {
      selfTile <- Ref.make[Option[TileInfo]](None)
      tiles <- Ref.make[Map[Byte, TileInfo]](Map.empty)
    } yield new RoomController(selfTile, tiles)
}
