package com.authentisign.desktop.camera;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.Base64;

public class CameraCapture {
    //הטענת הספריה
    static {
        nu.pattern.OpenCV.loadLocally();
    }

    private final VideoCapture capture;
    private final Mat frame;

    public CameraCapture() {
        this.capture = new VideoCapture();
        this.frame = new Mat();
    }

    //הפעלת המצלמה והגדרת רזולוציה
    public boolean startCamera(int width, int height) {
        if (!capture.isOpened()) {
            //מנסה לפתוח את המצלמה מספר 0 - ברירת מחדל
            capture.open(0);
            if (capture.isOpened()) {
                capture.set(Videoio.CAP_PROP_FRAME_WIDTH, width);
                capture.set(Videoio.CAP_PROP_FRAME_HEIGHT, height);
                return true;
            }
        }
        return capture.isOpened();
    }

    //סגירת המצלמה ושחרור משאבים
    public void stopCamera() {
        if (capture.isOpened()) capture.release();
    }

    //קראית תמונה אחת מהמצלמה ושמה בתוך ה-frame
    public Mat grabFrame() {
        if (capture.isOpened() && capture.read(frame))
            return frame;

        System.out.println("Failed to read frame");
        return null;
    }

    public static Image matToFxImage(Mat frame) {
        int type = (frame.channels() == 1) ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_3BYTE_BGR;
        BufferedImage bufferedImage = new BufferedImage(frame.cols(), frame.rows(), type);
        byte[] data = new byte[frame.rows() * frame.cols() * (int)frame.elemSize()];
        frame.get(0, 0, data);
        System.arraycopy(data, 0, ((DataBufferByte) bufferedImage.getRaster().getDataBuffer()).getData(), 0, data.length);
        return SwingFXUtils.toFXImage(bufferedImage, null);
    }

    //המרת תמונה למחרוזת טקטסט ארוכה
    public static String encodeMatToBase64(Mat frame) {
        MatOfByte buffer = new MatOfByte();
        Imgcodecs.imencode(".png", frame, buffer);
        return Base64.getEncoder().encodeToString(buffer.toArray());
    }

}