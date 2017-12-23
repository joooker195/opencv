import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

import java.util.List;
import java.util.Vector;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
public class Controller
{
    @FXML
    private Button cameraButton;
    // Текущий кадр
    @FXML
    private ImageView originalFrame;
    // Маска
    @FXML
    private ImageView maskImage;

    String colorName = "";



    //Задаём ширину и высоту кадра, с которым будем работать.
    final int WIDTH = 160, HEIGHT = 120;
    //Берём пробу цвета в центре кадра.
    final Scalar RED = new Scalar(0, 0, 255);
    final Scalar BLUE = new Scalar(255, 0, 0);


    // Таймер для получения видео потока
    private ScheduledExecutorService timer;
    // Объект OpenCV который производит захват изображения
    private VideoCapture capture = new VideoCapture();

    private boolean cameraActive;

    //Функция получает пробу цвета с камеры.
    private static double[] getSampleColor(VideoCapture videoCapture,
                                           int imageWidth, int imageHeight) {
        Mat image = new Mat();
        Size ksize  = new Size(5, 5);
        double h = 0, s = 0, v = 0;
        int sampleCount = 7; //Количество проб.
        for (int i = 0; i < sampleCount; ) {
            if (videoCapture.read(image) && !image.empty()) {
                Rect snippetROI = new Rect(image.width() / 2 - 5,
                        image.height() / 2 - 5, 10, 10);
                Mat snippet    = image.submat(snippetROI);
                Imgproc.blur(snippet, snippet, ksize);
                Imgproc.cvtColor(snippet, snippet, Imgproc.COLOR_BGR2HSV);
                double[] color = snippet.get(5, 5);
                h += color[0];
                s += color[1];
                v += color[2];
                i++;
            }
        }
        return new double[] {Math.round(h / sampleCount),
                Math.round(s / sampleCount),
                Math.round(v / sampleCount)};
    }

    @FXML
    private void startCamera() {

        this.imageViewProperties(this.originalFrame, 700);
        this.imageViewProperties(this.maskImage, 700);

        if (!this.cameraActive) {
            // Запустить камеру
            this.capture.open(0);

            // Видеопоток доступен?
            if (this.capture.isOpened()) {
                this.cameraActive = true;

                // Захватывать и обрабатывать кадры в отдельном потоке
                Runnable frameGrabber = () -> {
                    Mat frame = grabFrame();
                    // Сконвертировать и показать кадр
                    Image imageToShow = Utils.mat2Image(frame);
                    updateImageView(originalFrame, imageToShow);
                };

                this.timer = Executors.newSingleThreadScheduledExecutor();

                // Захватывать кадр каждые 33 мс (30 кадров в секунду)
                this.timer.scheduleAtFixedRate(frameGrabber, 0, 33, TimeUnit.MILLISECONDS);

                this.cameraButton.setText("Остановить захват изображения с камеры");
            } else {
                System.err.println("Ошибка при подключении к камере...");
            }
        } else {
            this.cameraActive = false;
            this.cameraButton.setText("Запустить захват изображения с камеры");
            this.stopAcquisition();
        }
    }

    /**
     * Получить кадр из открытого видеопотока
     */
    private Mat grabFrame() {
        Mat frame = new Mat();
        Mat gray = new Mat();
        Mat mask = new Mat();
        Mat circles = new Mat();

        this.capture.read(frame);
        Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.medianBlur(gray, gray, 5);
        Imgproc.Canny(gray, mask, 50, 200);
        Imgproc.HoughCircles(mask, circles, Imgproc.HOUGH_GRADIENT, 1, 120, 100, 30, 0, 0);
        this.updateImageView(this.maskImage, Utils.mat2Image(mask));


        int x = circles.cols();
        int y = circles.rows();

        Point center = new Point();
        int r = 0;
        double contourArea, maxArea = 0;
        MatOfPoint biggestContour = null;

        Mat hierarchy = new Mat();
        List<MatOfPoint> contours = new Vector<>();
        contours.clear();
        Imgproc.findContours(mask, contours, hierarchy,
                Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);


        for (MatOfPoint contour : contours) {
            contourArea = Imgproc.contourArea(contour);
            if (contourArea > maxArea) {
                biggestContour = contour;
            }
        }
            Rect rect = Imgproc.boundingRect(biggestContour);
            x = rect.x/2+170;
            y = rect.y/2+150;
            r = Math.max(rect.width, rect.height) * 2;


        Imgproc.circle(frame, new Point(x, y), 60, new Scalar(0, 255, 0), 2); // draw the outer circle
        Imgproc.circle(frame, new Point(x, y), 2, new Scalar(0, 0, 255), 3); // draw the center of the circle
        Imgproc.circle(frame, new Point(x, y), 60, new Scalar(0, 255, 0), 2); // draw the outer circle

        return frame;
    }
    /**
     * Установка ширины окон в UI
     */
    private void imageViewProperties(ImageView image, int dimension) {
        image.setFitWidth(dimension);
        image.setPreserveRatio(true);
    }

    /**
     * Обновить текущие кадры в UI
     */
    private void updateImageView(ImageView view, Image image) {
        Utils.onFXThread(view.imageProperty(), image);
    }

    /**
     * Остановить захват изображения и освободить все ресурсы
     */
    private void stopAcquisition() {
        if (this.timer != null && !this.timer.isShutdown()) {
            try {
                // Остановить таймер
                this.timer.shutdown();
                this.timer.awaitTermination(33, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                System.err.println("При попытке остановить захват изображения с камеры возникла ошибка " + e);
            }
        }

        if (this.capture.isOpened()) {
            // Освободить ресуры камеры
            this.capture.release();
        }
    }

    /**
     * При закрытии приложения - остановить захват изображений с камеры
     */
    protected void setClosed() {
        this.stopAcquisition();
    }
}
