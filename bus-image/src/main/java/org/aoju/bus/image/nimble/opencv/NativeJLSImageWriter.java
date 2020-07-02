/*********************************************************************************
 *                                                                               *
 * The MIT License (MIT)                                                         *
 *                                                                               *
 * Copyright (c) 2015-2020 aoju.org and other contributors.                      *
 *                                                                               *
 * Permission is hereby granted, free of charge, to any person obtaining a copy  *
 * of this software and associated documentation files (the "Software"), to deal *
 * in the Software without restriction, including without limitation the rights  *
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell     *
 * copies of the Software, and to permit persons to whom the Software is         *
 * furnished to do so, subject to the following conditions:                      *
 *                                                                               *
 * The above copyright notice and this permission notice shall be included in    *
 * all copies or substantial portions of the Software.                           *
 *                                                                               *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR    *
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,      *
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE   *
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER        *
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, *
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN     *
 * THE SOFTWARE.                                                                 *
 ********************************************************************************/
package org.aoju.bus.image.nimble.opencv;

import org.aoju.bus.core.lang.Normal;
import org.aoju.bus.image.nimble.codec.BytesWithImageDescriptor;
import org.aoju.bus.image.nimble.codec.ImageDescriptor;
import org.aoju.bus.logger.Logger;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;

import javax.imageio.*;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.nio.ByteOrder;

/**
 * @author Kimi Liu
 * @version 6.0.1
 * @since JDK 1.8+
 */
public class NativeJLSImageWriter extends ImageWriter {

    NativeJLSImageWriter(ImageWriterSpi originatingProvider) {
        super(originatingProvider);
    }

    @Override
    public ImageWriteParam getDefaultWriteParam() {
        return new JPEGLSImageWriteParam(getLocale());
    }

    @Override
    public void write(IIOMetadata streamMetadata, IIOImage image, ImageWriteParam param) throws IOException {
        if (output == null) {
            throw new IllegalStateException("input cannot be null");
        }

        if (!(output instanceof ImageOutputStream)) {
            throw new IllegalArgumentException("input is not an ImageInputStream!");
        }
        ImageOutputStream stream = (ImageOutputStream) output;
        stream.setByteOrder(ByteOrder.LITTLE_ENDIAN);

        if (!(stream instanceof BytesWithImageDescriptor)) {
            throw new IllegalArgumentException("stream does not implement BytesWithImageImageDescriptor!");
        }
        ImageDescriptor desc = ((BytesWithImageDescriptor) stream).getImageDescriptor();

        RenderedImage renderedImage = image.getRenderedImage();
        Mat buf = null;
        MatOfInt dicomParams = null;
        try {
            ImageCV mat = null;
            try {
                // 将交叉模式(PlanarConfiguration = 1)转换为像素模式，因此输入图像始终具有像素模式(PlanarConfiguration = 0)
                mat = ImageConversion.toMat(renderedImage, param.getSourceRegion(), false);

                int jpeglsNLE = param instanceof JPEGLSImageWriteParam ? ((JPEGLSImageWriteParam) param).getNearLossless() : 0;
                int bitCompressed = desc.getBitsCompressed();
                int cvType = mat.type();
                int channels = CvType.channels(cvType);
                boolean signed = desc.isSigned();
                int dcmFlags = signed ? Imgcodecs.DICOM_FLAG_SIGNED : Imgcodecs.DICOM_FLAG_UNSIGNED;
                if (signed) {
                    Logger.warn("Force compression to JPEG-LS lossless as lossy is not adapted to signed data.");
                    jpeglsNLE = 0;
                    bitCompressed = 16; // 扩展到分配的位以避免异常，因为将负值视为大正值
                }

                int[] params = new int[15];
                params[Imgcodecs.DICOM_PARAM_IMREAD] = Imgcodecs.IMREAD_UNCHANGED; // Image flags
                params[Imgcodecs.DICOM_PARAM_DCM_IMREAD] = dcmFlags; // DICOM flags
                params[Imgcodecs.DICOM_PARAM_WIDTH] = mat.width(); // Image width
                params[Imgcodecs.DICOM_PARAM_HEIGHT] = mat.height(); // Image height
                params[Imgcodecs.DICOM_PARAM_COMPRESSION] = Imgcodecs.DICOM_CP_JPLS; // Type of compression
                params[Imgcodecs.DICOM_PARAM_COMPONENTS] = channels; // Number of components
                params[Imgcodecs.DICOM_PARAM_BITS_PER_SAMPLE] = bitCompressed; // Bits per sample
                params[Imgcodecs.DICOM_PARAM_INTERLEAVE_MODE] = Imgcodecs.ILV_SAMPLE; // Interleave mode
                params[Imgcodecs.DICOM_PARAM_JPEGLS_LOSSY_ERROR] = jpeglsNLE; // Lossy error for jpeg-ls

                dicomParams = new MatOfInt(params);
                buf = Imgcodecs.dicomJpgWrite(mat, dicomParams, Normal.EMPTY);
                if (buf.empty()) {
                    throw new IIOException("Native JPEG-LS encoding error: null image");
                }
            } finally {
                if (mat != null) {
                    mat.release();
                }
            }
            byte[] bSrcData = new byte[buf.width() * buf.height() * (int) buf.elemSize()];
            buf.get(0, 0, bSrcData);
            stream.write(bSrcData);
        } catch (Throwable t) {
            throw new IIOException("Native JPEG-LS encoding error", t);
        } finally {
            NativeImageReader.closeMat(dicomParams);
            NativeImageReader.closeMat(buf);
        }
    }

    @Override
    public IIOMetadata getDefaultStreamMetadata(ImageWriteParam param) {
        return null;
    }

    @Override
    public IIOMetadata getDefaultImageMetadata(ImageTypeSpecifier imageType, ImageWriteParam param) {
        return null;
    }

    @Override
    public IIOMetadata convertStreamMetadata(IIOMetadata inData, ImageWriteParam param) {
        return null;
    }

    @Override
    public IIOMetadata convertImageMetadata(IIOMetadata inData, ImageTypeSpecifier imageType, ImageWriteParam param) {
        return null;
    }

}
