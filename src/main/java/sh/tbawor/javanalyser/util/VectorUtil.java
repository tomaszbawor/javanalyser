package sh.tbawor.javanalyser.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;

/**
 * Utility class for vector operations used in the semantic search functionality
 */
@Component
@Slf4j
public class VectorUtil {

  /**
   * Converts a float array to a byte array for storage in the database
   */
  public byte[] floatArrayToByteArray(float[] floatArray) {
    ByteBuffer byteBuffer = ByteBuffer.allocate(floatArray.length * Float.BYTES);
    FloatBuffer floatBuffer = byteBuffer.asFloatBuffer();
    floatBuffer.put(floatArray);
    return byteBuffer.array();
  }

  /**
   * Converts a byte array back to a float array for vector calculations
   */
  public float[] byteArrayToFloatArray(byte[] byteArray) {
    ByteBuffer byteBuffer = ByteBuffer.wrap(byteArray);
    FloatBuffer floatBuffer = byteBuffer.asFloatBuffer();
    float[] floatArray = new float[byteArray.length / Float.BYTES];
    floatBuffer.get(floatArray);
    return floatArray;
  }

  /**
   * Calculate cosine similarity between two float arrays
   * Returns a value between -1 and 1, where 1 means perfect similarity
   */
  public float cosineSimilarity(float[] vectorA, float[] vectorB) {
    if (vectorA.length != vectorB.length) {
      throw new IllegalArgumentException("Vectors must have the same dimension. VectorA: " +
          vectorA.length + ", VectorB: " + vectorB.length);
    }

    float dotProduct = 0.0f;
    float normA = 0.0f;
    float normB = 0.0f;

    for (int i = 0; i < vectorA.length; i++) {
      dotProduct += vectorA[i] * vectorB[i];
      normA += vectorA[i] * vectorA[i];
      normB += vectorB[i] * vectorB[i];
    }

    if (normA <= 0 || normB <= 0) {
      return 0;
    }

    return dotProduct / (float) (Math.sqrt(normA) * Math.sqrt(normB));
  }

  /**
   * Calculate Euclidean distance between two float arrays
   * Smaller values indicate greater similarity
   */
  public float euclideanDistance(float[] vectorA, float[] vectorB) {
    if (vectorA.length != vectorB.length) {
      throw new IllegalArgumentException("Vectors must have the same dimension");
    }

    float sum = 0.0f;

    for (int i = 0; i < vectorA.length; i++) {
      float diff = vectorA[i] - vectorB[i];
      sum += diff * diff;
    }

    return (float) Math.sqrt(sum);
  }

  /**
   * Normalize a vector to unit length
   */
  public float[] normalize(float[] vector) {
    float sum = 0.0f;
    for (float v : vector) {
      sum += v * v;
    }

    float norm = (float) Math.sqrt(sum);

    if (norm <= 0) {
      return new float[vector.length]; // Return zero vector if norm is zero
    }

    float[] normalized = new float[vector.length];
    for (int i = 0; i < vector.length; i++) {
      normalized[i] = vector[i] / norm;
    }

    return normalized;
  }

  /**
   * Calculate average vector from a list of vectors
   */
  public float[] averageVectors(float[][] vectors) {
    if (vectors.length == 0) {
      return new float[0];
    }

    int dimensions = vectors[0].length;
    float[] result = new float[dimensions];

    for (float[] vector : vectors) {
      if (vector.length != dimensions) {
        throw new IllegalArgumentException("All vectors must have the same dimension");
      }

      for (int i = 0; i < dimensions; i++) {
        result[i] += vector[i];
      }
    }

    for (int i = 0; i < dimensions; i++) {
      result[i] /= vectors.length;
    }

    return result;
  }

  /**
   * Debug utility to print vector stats
   */
  public void printVectorStats(float[] vector) {

    float min = Float.MAX_VALUE;
    float max = Float.MIN_VALUE;
    float sum = 0.0f;
    float normSum = 0.0f;

    for (float v : vector) {
      min = Math.min(min, v);
      max = Math.max(max, v);
      sum += v;
      normSum += v * v;
    }

    min = (vector.length > 0) ? min : 0;
    max = (vector.length > 0) ? max : 0;
    float avg = (vector.length > 0) ? sum / vector.length : 0;
    float norm = (float) Math.sqrt(normSum);

    log.debug("Vector stats - length: {}, min: {}, max: {}, avg: {}, norm: {}",
        vector.length, min, max, avg, norm);
  }

  /**
   * Compare two vectors for approximate equality
   */
  public boolean vectorsEqual(float[] vectorA, float[] vectorB, float tolerance) {
    if (vectorA.length != vectorB.length) {
      return false;
    }

    for (int i = 0; i < vectorA.length; i++) {
      if (Math.abs(vectorA[i] - vectorB[i]) > tolerance) {
        return false;
      }
    }

    return true;
  }
}
