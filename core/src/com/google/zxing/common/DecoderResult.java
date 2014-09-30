/*
 * Copyright 2007 ZXing authors
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

package com.google.zxing.common;

import java.util.List;

/**
 * <p>Encapsulates the result of decoding a matrix of bits. This typically
 * applies to 2D barcode formats. For now it contains the raw bytes obtained,
 * as well as a String interpretation of those bytes, if applicable.</p>
 *
 * @author Sean Owen
 */
public final class DecoderResult {

  private final byte[] rawBytes;
  private final String text;
  private final byte[] data;
  private final List<byte[]> byteSegments;
  private final String ecLevel;

  private int index;
  private int total;
  private int parity;

  public DecoderResult(byte[] rawBytes,
                       String text,
                       List<byte[]> byteSegments,
                       String ecLevel) {
      this(rawBytes, text, null, byteSegments, ecLevel);
  }

  public DecoderResult(byte[] rawBytes,
                       byte[] data,
                       List<byte[]> byteSegments,
                       String ecLevel) {
      this(rawBytes, null, data, byteSegments, ecLevel);
  }

  public DecoderResult(byte[] rawBytes,
                       String text,
                       byte[] data,
                       List<byte[]> byteSegments,
                       String ecLevel) {
    this.rawBytes = rawBytes;
    this.text = text;
    this.data = data;
    this.byteSegments = byteSegments;
    this.ecLevel = ecLevel;
  }
  
  public void addStructuredAppendInfo(int index, int total, int parity) {
      this.index = index;
      this.total = total;
      this.parity = parity;
  }

  public byte[] getRawBytes() {
    return rawBytes;
  }

  public String getText() {
    return text;
  }

  public byte[] getBinaryData() {
    return data;
  }

  public List<byte[]> getByteSegments() {
    return byteSegments;
  }

  public String getECLevel() {
    return ecLevel;
  }
  
  public boolean isPartOfStructuredAppend() {
      return total > 0;
  }
  
  public int getIndex() {
      return index;
  }
  
  public int getTotal() {
      return total;
  }

  public int getParity() {
      return parity;
  }
}