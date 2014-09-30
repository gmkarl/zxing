/*
 * Copyright 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.qrcode;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.Writer;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.decoder.Mode;
import com.google.zxing.qrcode.decoder.Version;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;

/**
 * This object renders a QR Code as a BitMatrix 2D array of greyscale values.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class QRCodeWriter implements Writer {

  private static final int QUIET_ZONE_SIZE = 4;

  @Override
  public BitMatrix encode(String contents, BarcodeFormat format, int width, int height)
      throws WriterException {

    return encode(contents, format, width, height, null);
  }

  @Override
  public BitMatrix encode(String contents,
                          BarcodeFormat format,
                          int width,
                          int height,
                          Map<EncodeHintType,?> hints) throws WriterException {

    if (contents.length() == 0) {
      throw new IllegalArgumentException("Found empty contents");
    }

    if (format != BarcodeFormat.QR_CODE) {
      throw new IllegalArgumentException("Can only encode QR_CODE, but got " + format);
    }

    if (width < 0 || height < 0) {
      throw new IllegalArgumentException("Requested dimensions are too small: " + width + 'x' +
          height);
    }

    ErrorCorrectionLevel errorCorrectionLevel = ErrorCorrectionLevel.L;
    if (hints != null) {
      ErrorCorrectionLevel requestedECLevel = (ErrorCorrectionLevel) hints.get(EncodeHintType.ERROR_CORRECTION);
      if (requestedECLevel != null) {
        errorCorrectionLevel = requestedECLevel;
      }
    }

    QRCode code = new QRCode();
    Encoder.encode(contents, errorCorrectionLevel, hints, code);
    return renderResult(code, width, height);
  }

  public BitMatrix encode(byte[] contents, int width, int height)
      throws WriterException {

    return encode(contents, width, height, null);
  }

  public BitMatrix encode(byte[] contents,
                          int width,
                          int height,
                          Map<EncodeHintType,?> hints) throws WriterException {

    if (contents.length == 0) {
      throw new IllegalArgumentException("Found empty contents");
    }

    if (width < 0 || height < 0) {
      throw new IllegalArgumentException("Requested dimensions are too small: " + width + 'x' +
          height);
    }

    ErrorCorrectionLevel errorCorrectionLevel = ErrorCorrectionLevel.L;
    if (hints != null) {
      ErrorCorrectionLevel requestedECLevel = (ErrorCorrectionLevel) hints.get(EncodeHintType.ERROR_CORRECTION);
      if (requestedECLevel != null) {
        errorCorrectionLevel = requestedECLevel;
      }
    }

    QRCode code = new QRCode();
    Encoder.encode(contents, 0, contents.length, errorCorrectionLevel, hints, code);
    return renderResult(code, width, height);
  }

  public BitMatrix[] encodeInStructuredAppend(String contents, int versionNumber, int dpm)
  	throws WriterException {

      return encodeInStructuredAppend(contents, versionNumber, dpm, null);
  }

  public BitMatrix[] encodeInStructuredAppend(String contents, int versionNumber, int dpm, Map<EncodeHintType,Object> hints)
      throws WriterException {

      if (contents.length() == 0) {
	  throw new IllegalArgumentException("Found empty contents");
      }

      if (dpm < 1) {
	  throw new IllegalArgumentException("Requested dots-per-module must be positive");
      }

      ErrorCorrectionLevel errorCorrectionLevel = ErrorCorrectionLevel.L;
      if (hints != null) {
        ErrorCorrectionLevel requestedECLevel = (ErrorCorrectionLevel) hints.get(EncodeHintType.ERROR_CORRECTION);
        if (requestedECLevel != null) {
          errorCorrectionLevel = requestedECLevel;
        }
      }

      String encoding = hints == null ? null : (String) hints.get(EncodeHintType.CHARACTER_SET);
      if (encoding == null) {
        encoding = Encoder.DEFAULT_BYTE_MODE_ENCODING;
      }

      if (hints == null) {
	  hints = new HashMap<EncodeHintType, Object>();
      }
      hints.put(EncodeHintType.FORCE_8BIT_MODE, Boolean.TRUE);

      byte[] data;
      try {
	  data = contents.getBytes(encoding);
      } catch (UnsupportedEncodingException uee) {
	  throw new WriterException(uee.toString());
      }
      int parity = data[0];
      for (int i = 1; i < data.length; i++) {
	  parity ^= data[i];
      }
      Version version = Version.getVersionForNumber(versionNumber);
      Mode mode = Mode.BYTE;
      int numBytes = version.getTotalCodewords();
      Version.ECBlocks ecBlocks = version.getECBlocksForLevel(errorCorrectionLevel);
      int numEcBytes = ecBlocks.getTotalECCodewords();
      int numRSBlocks = ecBlocks.getNumBlocks();
      int numDataBytes = numBytes - numEcBytes;
      int numECIBytes = Encoder.DEFAULT_BYTE_MODE_ENCODING.equals(encoding) ? 0 : 4 + 16;
      // reserve space for SA header + ECI header + Mode + Count
      int numRealDataBytes = numDataBytes - (4 + 16 + numECIBytes + 4 + mode.getCharacterCountBits(version) + 7) / 8;
      
      // calculate number of bytes into a symbol in order to not split a multi-byte char into two symbols
      List<Integer> split = new ArrayList<Integer>();
      int offset = 0;
      while (offset < contents.length()) {
	  int len = Math.min(numRealDataBytes, contents.length() - offset);
	  try {
	      while (contents.substring(offset, offset + len).getBytes(encoding).length > numRealDataBytes) len--;
	  } catch (UnsupportedEncodingException uee) {
	      throw new WriterException(uee.toString());
	  }
	  split.add(len);
	  offset += len;
      }
      int total = split.size();
      hints.put(EncodeHintType.STRUCTURED_APPEND_TOTAL, total);
      hints.put(EncodeHintType.STRUCTURED_APPEND_PARITY, parity & 0xFF);
      BitMatrix[] res = new BitMatrix[total];
      offset = 0;
      for (int i = 0; i < total; i++) {
	  hints.put(EncodeHintType.STRUCTURED_APPEND_INDEX, i + 1);
	  QRCode code = new QRCode();
	  code.setECLevel(errorCorrectionLevel);
	  code.setMode(mode);
	  code.setVersion(versionNumber);
	  code.setNumTotalBytes(numBytes);
	  code.setNumDataBytes(numDataBytes);
	  code.setNumRSBlocks(numRSBlocks);
	  code.setNumECBytes(numEcBytes);
	  code.setMatrixWidth(version.getDimensionForVersion());
	  int len = split.get(i);
	  Encoder.encode(contents.substring(offset, offset + len), errorCorrectionLevel, hints, code);
	  res[i] = renderResult(code, dpm);
	  offset += len;
      }
      return res;
  }

  public BitMatrix[] encodeInStructuredAppend(byte[] contents, int versionNumber, int dpm)
  	throws WriterException {

      return encodeInStructuredAppend(contents, versionNumber, dpm, null);
  }

  public BitMatrix[] encodeInStructuredAppend(byte[] contents, int versionNumber, int dpm, Map<EncodeHintType,Object> hints)
  throws WriterException {

      if (contents.length == 0) {
	  throw new IllegalArgumentException("Found empty contents");
      }

      if (dpm < 1) {
	  throw new IllegalArgumentException("Requested dots-per-module must be positive");
      }

      ErrorCorrectionLevel errorCorrectionLevel = ErrorCorrectionLevel.L;
      if (hints != null) {
        ErrorCorrectionLevel requestedECLevel = (ErrorCorrectionLevel) hints.get(EncodeHintType.ERROR_CORRECTION);
        if (requestedECLevel != null) {
          errorCorrectionLevel = requestedECLevel;
        }
      }

      if (hints == null) {
	  hints = new HashMap<EncodeHintType, Object>();
      }
      
      int parity = contents[0];
      for (int i = 1; i < contents.length; i++) {
	  parity ^= contents[i];
      }
      Version version = Version.getVersionForNumber(versionNumber);
      Mode mode = Mode.BYTE;
      int numBytes = version.getTotalCodewords();
      Version.ECBlocks ecBlocks = version.getECBlocksForLevel(errorCorrectionLevel);
      int numEcBytes = ecBlocks.getTotalECCodewords();
      int numRSBlocks = ecBlocks.getNumBlocks();
      int numDataBytes = numBytes - numEcBytes;
      int numECIBytes = 4 + 16;
      // reserve space for SA header + ECI header + Mode + Count
      int numRealDataBytes = numDataBytes - (4 + 16 + numECIBytes + 4 + mode.getCharacterCountBits(version) + 7) / 8;
      int total = (contents.length + numRealDataBytes - 1) / numRealDataBytes;
      hints.put(EncodeHintType.STRUCTURED_APPEND_TOTAL, total);
      hints.put(EncodeHintType.STRUCTURED_APPEND_PARITY, parity & 0xFF);
      BitMatrix[] res = new BitMatrix[total];
      for (int i = 0; i < total; i++) {
	  hints.put(EncodeHintType.STRUCTURED_APPEND_INDEX, i + 1);
	  QRCode code = new QRCode();
	  code.setECLevel(errorCorrectionLevel);
	  code.setMode(mode);
	  code.setVersion(version.getVersionNumber());
	  code.setNumTotalBytes(numBytes);
	  code.setNumDataBytes(numDataBytes);
	  code.setNumRSBlocks(numRSBlocks);
	  code.setNumECBytes(numEcBytes);
	  code.setMatrixWidth(version.getDimensionForVersion());
	  int offset = i * numRealDataBytes;
	  Encoder.encode(contents, offset, Math.min(numRealDataBytes, contents.length - offset), errorCorrectionLevel, hints, code);
	  res[i] = renderResult(code, dpm);
      }
      return res;
  }

  // Note that the input matrix uses 0 == white, 1 == black, while the output matrix uses
  // 0 == black, 255 == white (i.e. an 8 bit greyscale bitmap).
  private static BitMatrix renderResult(QRCode code, int width, int height) {
    ByteMatrix input = code.getMatrix();
    if (input == null) {
      throw new IllegalStateException();
    }
    int inputWidth = input.getWidth();
    int inputHeight = input.getHeight();
    int qrWidth = inputWidth + (QUIET_ZONE_SIZE << 1);
    int qrHeight = inputHeight + (QUIET_ZONE_SIZE << 1);
    int outputWidth = Math.max(width, qrWidth);
    int outputHeight = Math.max(height, qrHeight);

    int multiple = Math.min(outputWidth / qrWidth, outputHeight / qrHeight);
    // Padding includes both the quiet zone and the extra white pixels to accommodate the requested
    // dimensions. For example, if input is 25x25 the QR will be 33x33 including the quiet zone.
    // If the requested size is 200x160, the multiple will be 4, for a QR of 132x132. These will
    // handle all the padding from 100x100 (the actual QR) up to 200x160.
    int leftPadding = (outputWidth - (inputWidth * multiple)) / 2;
    int topPadding = (outputHeight - (inputHeight * multiple)) / 2;

    BitMatrix output = new BitMatrix(outputWidth, outputHeight);

    for (int inputY = 0, outputY = topPadding; inputY < inputHeight; inputY++, outputY += multiple) {
      // Write the contents of this row of the barcode
      for (int inputX = 0, outputX = leftPadding; inputX < inputWidth; inputX++, outputX += multiple) {
        if (input.get(inputX, inputY) == 1) {
          output.setRegion(outputX, outputY, multiple, multiple);
        }
      }
    }

    return output;
  }

  // Note that the input matrix uses 0 == white, 1 == black, while the output matrix uses
  // 0 == black, 255 == white (i.e. an 8 bit greyscale bitmap).
  private static BitMatrix renderResult(QRCode code, int dpm) {
    ByteMatrix input = code.getMatrix();
    if (input == null) {
      throw new IllegalStateException();
    }
    int inputWidth = input.getWidth();
    int inputHeight = input.getHeight();
    int qrWidth = inputWidth + QUIET_ZONE_SIZE;
    int qrHeight = inputHeight + QUIET_ZONE_SIZE;
    int outputWidth = qrWidth * dpm;
    int outputHeight = qrHeight * dpm;

    int leftPadding = QUIET_ZONE_SIZE * dpm / 2;
    int topPadding = QUIET_ZONE_SIZE *dpm / 2;

    BitMatrix output = new BitMatrix(outputWidth, outputHeight);

    for (int inputY = 0, outputY = topPadding; inputY < inputHeight; inputY++, outputY += dpm) {
      // Write the contents of this row of the barcode
      for (int inputX = 0, outputX = leftPadding; inputX < inputWidth; inputX++, outputX += dpm) {
        if (input.get(inputX, inputY) == 1) {
          output.setRegion(outputX, outputY, dpm, dpm);
        }
      }
    }

    return output;
  }

}
