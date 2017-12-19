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

        // Видеопоток доступен?
        if (this.capture.isOpened()) {
            try {
                double[] sampleColor = getSampleColor(capture, WIDTH, HEIGHT);
                double h = sampleColor[0];
                String colorName = "";
                if (h < 22)
                    colorName = "orange";
                else if (h < 38)
                    colorName = "yellow";
                else if (h < 75)
                    colorName = "green";
                else if (h < 130)
                    colorName = "blue";
                else if (h < 160)
                    colorName = "violet";
                else
                    colorName = "red";
                //System.out.println("color name = "+colorName);
                double hMin = sampleColor[0] - 10;
                double hMax = sampleColor[0] + 10;
                if (hMin < 0)
                    hMin = 0;
                if (hMax > 179)
                    hMax = 179;
                double sMin = sampleColor[1] - 50;
                double sMax = sampleColor[1] + 50;
                if (sMin < 0)
                    sMin = 0;
                if (sMax > 255)
                    sMax = 255;
                double vMin = sampleColor[2] - 50;
                double vMax = sampleColor[2] + 50;
                if (vMin < 0)
                    vMin = 0;
                if (vMax > 255)
                    vMax = 255;
                Scalar minValues = new Scalar(hMin, sMin, vMin);
                Scalar maxValues = new Scalar(hMax, sMax, vMax);

                Mat hsv = new Mat();
                Mat mask = new Mat();
                // Считать текущий кадр
                this.capture.read(frame);

                // Если кадр не пуст - обработать его
                if (!frame.empty()) {
                    Mat blurredImage = new Mat();
                    Mat hsvImage = new Mat();


                    // Сконвертировать кадр в HSV
                    Imgproc.cvtColor(frame, hsv, Imgproc.COLOR_BGR2GRAY);

                    // Удалить шум
                    Imgproc.blur(hsv, hsvImage, new Size(7, 7));

                   /* Scalar minValues = new Scalar(12 , 149,255);
                    Scalar maxValues = new Scalar(205,250,255);*/


                    // Порог для HSV изображения для поиска шара
                    Core.inRange(hsvImage, minValues, maxValues, mask);

                    // Показать маску
                    this.updateImageView(this.maskImage, Utils.mat2Image(mask));

                    // Найти контура шаров и показать их
                    frame = this.findAndDrawBalls(mask, frame);
                }
            } catch (Exception e) {
                System.err.print("Ошибка при обработки изображения");
                e.printStackTrace();
            }
        }
        return frame;
    }

    /**
     * Найти и обозначить контуры на изображении
     */
    private Mat findAndDrawBalls(Mat maskedImage, Mat frame) {
      //  List<MatOfPoint> contours = new ArrayList<>();
        //Mat hierarchy = new Mat();

        Mat hierarchy = new Mat();
        Size ksize  = new Size(5, 5);
        List<MatOfPoint> contours = new Vector<MatOfPoint>();
        double contourArea, maxArea = 0;
        MatOfPoint biggestContour = null;
        MatOfPoint2f contours2f = new MatOfPoint2f();
        Point center = new Point();
        float[] radius = new float[1];
        MatOfByte buffer = new MatOfByte();

        contours.clear();
        Imgproc.findContours(maskedImage, contours, hierarchy,
                Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);

        //Среди найденных контуров ищем контур наибольшего размера.
        for (MatOfPoint contour : contours) {
            contourArea = Imgproc.contourArea(contour);
            if (contourArea > maxArea) {
                biggestContour = contour;
                maxArea = contourArea;
            }
        }

        /*double saveArea = 0;
        for(int i=0;i<3 ;i++)
        {
            for (MatOfPoint contour : contours) {
                contourArea = Imgproc.contourArea(contour);
                if (contourArea > maxArea) {
                    biggestContour = contour;
                    maxArea = contourArea;
                }
            }
        }
        */

        System.out.println("max area = "+maxArea);

        if (biggestContour != null && maxArea >= 3000 && maxArea<=7000) {
            Rect rect = Imgproc.boundingRect(biggestContour);
            center.x = rect.x + rect.width / 2;
            center.y = rect.y + rect.height / 2;
            radius[0] = Math.max(rect.width, rect.height) / 2;

            contours.clear();
            contours.add(biggestContour);
            //Рисуем найденный контур на кадре.
            Imgproc.drawContours(frame, contours, 0, BLUE);
            //Рисуем найденный круг на кадре(если это круг).
            if(Math.abs(rect.height - rect.width)<=50) {
                Imgproc.circle(frame, center, (int) radius[0], RED);
            }
        }
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
