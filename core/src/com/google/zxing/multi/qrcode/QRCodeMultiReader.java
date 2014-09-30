/*
 * Copyright 2009 ZXing authors
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

package com.google.zxing.multi.qrcode;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.NotFoundException;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.ResultPoint;
import com.google.zxing.common.DecoderResult;
import com.google.zxing.common.DetectorResult;
import com.google.zxing.multi.MultipleBarcodeReader;
import com.google.zxing.multi.qrcode.detector.MultiDetector;
import com.google.zxing.qrcode.QRCodeReader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This implementation can detect and decode multiple QR Codes in an image.
 *
 * @author Sean Owen
 * @author Hannes Erven
 */
public final class QRCodeMultiReader extends QRCodeReader implements MultipleBarcodeReader {

  private static final Result[] EMPTY_RESULT_ARRAY = new Result[0];

  @Override
  public Result[] decodeMultiple(BinaryBitmap image) throws NotFoundException {
    return decodeMultiple(image, null);
  }

  @Override
  public Result[] decodeMultiple(BinaryBitmap image, Map<DecodeHintType,?> hints) throws NotFoundException {
    List<Result> results = new ArrayList<Result>();
    DetectorResult[] detectorResults = new MultiDetector(image.getBlackMatrix()).detectMulti(hints);
    for (DetectorResult detectorResult : detectorResults) {
      try {
        DecoderResult decoderResult = getDecoder().decode(detectorResult.getBits());
        ResultPoint[] points = detectorResult.getPoints();
        Result result = new Result(decoderResult.getText(), decoderResult.getBinaryData(), decoderResult.getRawBytes(), points,
                                   BarcodeFormat.QR_CODE);
        List<byte[]> byteSegments = decoderResult.getByteSegments();
        if (byteSegments != null) {
          result.putMetadata(ResultMetadataType.BYTE_SEGMENTS, byteSegments);
        }
        String ecLevel = decoderResult.getECLevel();
        if (ecLevel != null) {
          result.putMetadata(ResultMetadataType.ERROR_CORRECTION_LEVEL, ecLevel);
        }
        if (decoderResult.isPartOfStructuredAppend()) {
            result.putMetadata(ResultMetadataType.STRUCTURED_APPEND_INDEX, decoderResult.getIndex());
            result.putMetadata(ResultMetadataType.STRUCTURED_APPEND_TOTAL, decoderResult.getTotal());
            result.putMetadata(ResultMetadataType.STRUCTURED_APPEND_PARITY, decoderResult.getParity());
        }
        results.add(result);
      } catch (ReaderException re) {
        // ignore and continue 
      }
    }
    if (results.isEmpty()) {
      return EMPTY_RESULT_ARRAY;
    } else {
      return results.toArray(new Result[results.size()]);
    }
  }
  
  public static Result[] reassembleStructuredAppendSymbols(Result[] symbols) {
      List<Result> results = new ArrayList<Result>();
      List<Result> starters = new ArrayList<Result>();
      for (Result s : symbols) {
          Integer index = (Integer) s.getResultMetadata().get(ResultMetadataType.STRUCTURED_APPEND_INDEX);
          if (index == null) {
              results.add(s);
          } else if (index == 1) {
              starters.add(s);
          }
      }
      for (Result s : starters) {
          Integer total = (Integer) s.getResultMetadata().get(ResultMetadataType.STRUCTURED_APPEND_TOTAL);
          Integer parity = (Integer) s.getResultMetadata().get(ResultMetadataType.STRUCTURED_APPEND_PARITY);
          List<Result> list = new ArrayList<Result>();
          list.add(s);
          for (int i = 2; i <= total; i++) {
              Result next = findSymbol(i, total, parity, symbols);
              if (next == null) break;
              list.add(next);
          }
          if (list.size() == total) {
              StringBuilder text = new StringBuilder();
              ByteArrayOutputStream data = new ByteArrayOutputStream();
              ByteArrayOutputStream rawBytes = new ByteArrayOutputStream();
              try {
        	  for (Result cur : list) {
        	      if (cur.getText() != null) {
        		  text.append(cur.getText());
        	      }
        	      if (cur.getBinaryData() != null) {
        		  data.write(cur.getBinaryData());
        	      }
        	      rawBytes.write(cur.getRawBytes());
        	  }
              } catch (IOException e) {}
              String resText = (text.length() > 0) ? text.toString() : null;
              byte[] resData = (data.size() > 0) ? data.toByteArray() : null;
              results.add(new Result(resText, resData, rawBytes.toByteArray(), null, BarcodeFormat.QR_CODE));
          }
      }
      if (results.isEmpty()) {
	      return EMPTY_RESULT_ARRAY;
	    } else {
	      return results.toArray(new Result[results.size()]);
	    }
  }
  
  private static Result findSymbol(Integer index, Integer total, Integer parity, Result[] symbols) {
      for (Result s : symbols) {
	  if (index.equals(s.getResultMetadata().get(ResultMetadataType.STRUCTURED_APPEND_INDEX)) &&
              total.equals(s.getResultMetadata().get(ResultMetadataType.STRUCTURED_APPEND_TOTAL)) &&
              parity.equals(s.getResultMetadata().get(ResultMetadataType.STRUCTURED_APPEND_PARITY))) {
	      return s;
	  }
      }
      return null;
  }

}
