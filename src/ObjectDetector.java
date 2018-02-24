/* Copyright 2018 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

import java.awt.AWTException;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

import org.opencv.core.Core;
import org.opencv.core.Rect2d;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Tensor;
import org.tensorflow.types.UInt8;

/**
 * Java inference for the Object Detection API at:
 * https://github.com/tensorflow/models/blob/master/research/object_detection/
 */
public class ObjectDetector {
  
	SavedModelBundle model;
	Robot robot;
	
	public ObjectDetector() throws AWTException {
		this.model = SavedModelBundle.load("/home/dpapp/tensorflow-1.5.0/models/raccoon_dataset/results/checkpoint_56749/saved_model/", "serve");
		this.robot = new Robot();
	}
	
	public void update() throws Exception {
		// TODO: eliminate IO and pass BufferedImage directly.
		/*String fileName = "/home/dpapp/Desktop/RunescapeAI/temp/screenshot.jpg";
		BufferedImage image = captureScreenshotGameWindow();
		ImageIO.write(image, "jpg", new File(fileName));
		this.detectedObjects = getRecognizedObjectsFromImage(fileName);*/
	}
	
	public ArrayList<DetectedObject> getObjectsInImage(BufferedImage image, double scoreThreshold) throws Exception {
		List<Tensor<?>> outputs = null;
		ArrayList<DetectedObject> detectedObjectsInImage = new ArrayList<DetectedObject>();
		
		long timebefore = System.currentTimeMillis();
		makeImageTensor(image);
		System.out.println("Conversion took: " + (System.currentTimeMillis() - timebefore));
        try (Tensor<UInt8> input = makeImageTensor(image)) {
        	long timebefore2 = System.currentTimeMillis();
          outputs =
              model
                  .session()
                  .runner()
                  .feed("image_tensor", input)
                  .fetch("detection_scores")
                  .fetch("detection_classes")
                  .fetch("detection_boxes")
                  .run();
          System.out.println("Model took: " + (System.currentTimeMillis() - timebefore));
        }
        
        try (Tensor<Float> scoresT = outputs.get(0).expect(Float.class);
        	Tensor<Float> classesT = outputs.get(1).expect(Float.class);
            Tensor<Float> boxesT = outputs.get(2).expect(Float.class)) {
           
            int maxObjects = (int) scoresT.shape()[1];
            float[] scores = scoresT.copyTo(new float[1][maxObjects])[0];
            float[] classes = classesT.copyTo(new float[1][maxObjects])[0];
            float[][] boxes = boxesT.copyTo(new float[1][maxObjects][4])[0];

            for (int i = 0; i < scores.length; ++i) {
              if (scores[i] > scoreThreshold) {
            	  detectedObjectsInImage.add(new DetectedObject(scores[i], classes[i], boxes[i]));
              }
            }
          }
        return detectedObjectsInImage;
	}
	
	/*public boolean isObjectPresentInBoundingBoxInImage(BufferedImage image, Rect2d boundingBox, String objectClass) throws Exception {
		BufferedImage subImage = image.getSubimage((int) boundingBox.x, (int) boundingBox.y, (int) boundingBox.width, (int) boundingBox.height);
		ArrayList<DetectedObject> detectedObjectsInSubImage = getObjectsInImage(subImage);
		return (getObjectsOfClassInList(detectedObjectsInSubImage, objectClass).size() != 0);
	}*/
	
	public boolean isObjectPresentInBoundingBoxInImage(ArrayList<DetectedObject> detectedObjects, Rect2d boundingBox, String objectClass) throws Exception {
		for (DetectedObject detectedObject : detectedObjects) {
			if (detectedObject.getDetectionClass().equals(objectClass)) {
				//System.out.println(("Required bounding box: " + (int) boundingBox.x + ", " + (int) boundingBox.y + ", " + (int) boundingBox.width + ", " + (int) boundingBox.height));
				//System.out.println(("Detected bounding box: " + (int) detectedObject.getBoundingRect2d().x + ", " + (int) detectedObject.getBoundingRect2d().y + ", " + (int) detectedObject.getBoundingRect2d().width + ", " + (int) detectedObject.getBoundingRect2d().height) + "\n");
				if ((Math.abs(detectedObject.getBoundingRect2d().x - boundingBox.x) < 10) &&
					(Math.abs(detectedObject.getBoundingRect2d().y - boundingBox.y) < 10) &&
					(Math.abs(detectedObject.getBoundingRect2d().width - boundingBox.width) < 10) &&
					(Math.abs(detectedObject.getBoundingRect2d().height - boundingBox.height) < 10)) {
					return true;
				}
			}
		}
		return false;
	}
    
	public ArrayList<DetectedObject> getObjectsOfClassInList(ArrayList<DetectedObject> detectedObjects, String objectClass) {
		ArrayList<DetectedObject> detectedObjectsOfType = new ArrayList<DetectedObject>();
		for (DetectedObject detectedObject : detectedObjects) {
			if (detectedObject.getDetectionClass().equals(objectClass)) {
				detectedObjectsOfType.add(detectedObject);
			}
		}
		return detectedObjectsOfType;
	}

  private static Tensor<UInt8> makeImageTensor(BufferedImage image) throws IOException {
	BufferedImage formattedImage = convertBufferedImage(image, BufferedImage.TYPE_3BYTE_BGR);
	byte[] data = ((DataBufferByte) formattedImage.getData().getDataBuffer()).getData();
	bgr2rgb(data);  
	
    // BufferedImage and ImageIO.read() seems to produce BGR-encoded images, but the model expects RGB.
    final long BATCH_SIZE = 1;
    final long CHANNELS = 3;
    long[] shape = new long[] {BATCH_SIZE, formattedImage.getHeight(), formattedImage.getWidth(), CHANNELS};
    return Tensor.create(UInt8.class, shape, ByteBuffer.wrap(data));
  }
  
  private static BufferedImage convertBufferedImage(BufferedImage sourceImage, int bufferedImageType) {
    BufferedImage image = new BufferedImage(sourceImage.getWidth(), sourceImage.getHeight(), bufferedImageType);
    Graphics2D g2d = image.createGraphics();
    g2d.drawImage(sourceImage, 0, 0, null);
    g2d.dispose();
    return image;
  }
  
  private static void bgr2rgb(byte[] data) {
	    for (int i = 0; i < data.length; i += 3) {
	      byte tmp = data[i];
	      data[i] = data[i + 2];
	      data[i + 2] = tmp;
	    }
	  }
  
  public BufferedImage captureScreenshotGameWindow() throws IOException, AWTException {
		Rectangle area = new Rectangle(Constants.GAME_WINDOW_OFFSET_X, Constants.GAME_WINDOW_OFFSET_Y, Constants.GAME_WINDOW_WIDTH, Constants.GAME_WINDOW_HEIGHT);
		return robot.createScreenCapture(area);
	  }
}