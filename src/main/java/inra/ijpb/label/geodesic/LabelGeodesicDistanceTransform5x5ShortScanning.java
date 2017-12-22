/**
 * 
 */
package inra.ijpb.label.geodesic;

import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import inra.ijpb.algo.AlgoStub;
import inra.ijpb.binary.ChamferWeights;

/**
 * @author dlegland
 *
 */
public class LabelGeodesicDistanceTransform5x5ShortScanning extends AlgoStub
		implements LabelGeodesicDistanceTransform
{
	// ==================================================
	// Class variables
	
	public static final short MAX_DIST = Short.MAX_VALUE;
	
	short[] weights = new short[]{5, 7, 11};

	/**
	 * Flag for dividing final distance map by the value first weight. 
	 * This results in distance map values closer to Euclidean distance. 
	 */
	boolean normalizeMap = true;

	int sizeX;
	int sizeY;

	/** The label image used as mask */
	ImageProcessor labelImage;
	
	/** the instance of ImageProcessor storing the result */
	ImageProcessor distMap;

	/** the flag indicating whether the image has been modified or not */
	boolean modif;

	
	// ==================================================
	// Constructors 
	
	public LabelGeodesicDistanceTransform5x5ShortScanning(ChamferWeights weights)
	{
		this(weights.getShortWeights(), true);
	}

	public LabelGeodesicDistanceTransform5x5ShortScanning(ChamferWeights weights, boolean normalize)
	{
		this(weights.getShortWeights(), normalize);
	}

	public LabelGeodesicDistanceTransform5x5ShortScanning(short[] weights)
	{
		this(weights, true);
	}

	public LabelGeodesicDistanceTransform5x5ShortScanning(short[] weights, boolean normalize)
	{
		this.weights = weights;
		this.normalizeMap = normalize;
		
		// ensure the number of weight is at least 3.
		if (weights.length < 3) 
		{
			short[] newWeights = new short[3];
			newWeights[0] = weights[0];
			newWeights[1] = weights[1];
			newWeights[2] = (short) (weights[0] + weights[1]);
			this.weights = newWeights;
		}
	}

	// ==================================================
	// Methods 
	
	/**
	 * Computes the geodesic distance function for each pixel in mask label
	 * image, using the given binary marker image. Mask and marker should be
	 * ImageProcessor the same size and containing integer values.
	 * 
	 * The function returns a new ShortProcessor the same size as the input,
	 * with values greater or equal to zero.
	 *
	 * @param marker
	 *            the binary marker image
	 * @param mask
	 *            the label image used as mask
	 * @return the geodesic distance map from the marker image within each label
	 *         of the mask
	 * @see inra.ijpb.label.geodesic.LabelGeodesicDistanceTransform#geodesicDistanceMap(ij.process.ImageProcessor,
	 *      ij.process.ImageProcessor)
	 */
	@Override
	public ImageProcessor geodesicDistanceMap(ImageProcessor marker, ImageProcessor mask)
	{
		// size of image
		sizeX = mask.getWidth();
		sizeY = mask.getHeight();
		
		// update mask
		this.labelImage = mask;

		// create new empty image, and fill it with black
		fireStatusChanged(this, "Initialization..."); 
		this.distMap = initialize(marker);

		int iter = 0;
		modif = true;
		while(modif)
		{
			modif = false;

			// forward iteration
			fireStatusChanged(this, "Forward iteration " + iter);
			forwardIteration();

			// backward iteration
			fireStatusChanged(this, "Backward iteration " + iter); 
			backwardIteration();

			// Iterate while pixels have been modified
			iter++;
		};

		// Normalize values by the first weight
		if (this.normalizeMap) 
		{
			fireStatusChanged(this, "Normalize map"); 
			for (int j = 0; j < sizeY; j++)
			{
				for (int i = 0; i < sizeX; i++) 
				{
					short val = (short) distMap.get(i, j);
					if (val != MAX_DIST)
					{
						distMap.set(i, j, val / this.weights[0]);
					}
				}
			}
		}
		
		// Compute max value within the mask
		fireStatusChanged(this, "Normalize display"); 
		float maxVal = 0;
		for (int i = 0; i < sizeX; i++)
		{
			for (int j = 0; j < sizeY; j++)
			{
				short val = (short) distMap.get(i, j);
				if (val != MAX_DIST)
				{
					maxVal = Math.max(maxVal, val);
				}
			}
		}
		// System.out.println("max value: " + Float.toString(maxVal));

		// update and return resulting Image processor
		distMap.setMinAndMax(0, maxVal);
		// Forces the display to non-inverted LUT
		if (distMap.isInvertedLut())
		{
			distMap.invertLut();
		}
		
		return distMap;
	}

	private ShortProcessor initialize(ImageProcessor marker)
	{
		// size of image
		sizeX = marker.getWidth();
		sizeY = marker.getHeight();
		
		ShortProcessor distMap = new ShortProcessor(sizeX, sizeY);
		distMap.setValue(0);
		distMap.fill();

		// initialize empty image with either 0 (foreground) or Inf (background)
		for (int y = 0; y < sizeY; y++) 
		{
			for (int x = 0; x < sizeX; x++) 
			{
				int val = marker.get(x, y) & 0x00ff;
				distMap.set(x, y, val == 0 ? Short.MAX_VALUE : 0);
			}
		}

		return distMap;
	}
	
	private void forwardIteration() 
	{
		// Initialize pairs of offset and weights
		int[] dx = new int[]{-1, +1,  -2, -1,  0, +1, +2,  -1};
		int[] dy = new int[]{-2, -2,  -1, -1, -1, -1, -1,   0};
		
		short[] dw = new short[] { 
				weights[2], weights[2], 
				weights[2], weights[1], weights[0], weights[1], weights[2], 
				weights[0] };
		
		// Iterate over pixels
		for (int y = 0; y < sizeY; y++)
		{
			this.fireProgressChanged(this, y, sizeY);
			for (int x = 0; x < sizeX; x++)
			{
				// get current label
				int label = (int) labelImage.getf(x, y);
				
				// do not process background pixels
				if (label == 0)
					continue;
				
				// current distance value
				int currentDist = distMap.get(x, y);
				int newDist = currentDist;
				
				// iterate over neighbors
				for (int i = 0; i < dx.length; i++)
				{
					// compute neighbor coordinates
					int x2 = x + dx[i];
					int y2 = y + dy[i];
					
					// check bounds
					if (x2 < 0 || x2 >= sizeX)
						continue;
					if (y2 < 0 || y2 >= sizeY)
						continue;
					
					if (((int) labelImage.getf(x2, y2)) == label)
					{
						// Increment distance
						newDist = Math.min(newDist, distMap.get(x2, y2) + dw[i]);
					}
				}
				
				if (newDist < currentDist) 
				{
					distMap.set(x, y, newDist);
					modif = true;
				}
			}
		}
		
		this.fireProgressChanged(this, sizeY, sizeY);
	}

	private void backwardIteration()
	{
		// Initialize pairs of offset and weights
		int[] dx = new int[]{+1, -1,  +2, +1,  0, -1, -2,  +1};
		int[] dy = new int[]{+2, +2,  +1, +1, +1, +1, +1,   0};
		
		short[] dw = new short[] { 
				weights[2], weights[2], 
				weights[2], weights[1], weights[0], weights[1], weights[2], 
				weights[0] };
		
		// Iterate over pixels
		for (int y = sizeY-1; y >= 0; y--)
		{
			this.fireProgressChanged(this, sizeY-1-y, sizeY);
			for (int x = sizeX-1; x >= 0; x--)
			{
				// get current label
				int label = (int) labelImage.getf(x, y);
				
				// do not process background pixels
				if (label == 0)
					continue;
				
				// current distance value
				int currentDist = distMap.get(x, y);
				int newDist = currentDist;
				
				// iterate over neighbors
				for (int i = 0; i < dx.length; i++)
				{
					// compute neighbor coordinates
					int x2 = x + dx[i];
					int y2 = y + dy[i];
					
					// check bounds
					if (x2 < 0 || x2 >= sizeX)
						continue;
					if (y2 < 0 || y2 >= sizeY)
						continue;
					
					if (((int) labelImage.getf(x2, y2)) == label)
					{
						// Increment distance
						newDist = Math.min(newDist, distMap.get(x2, y2) + dw[i]);
					}
				}
				
				if (newDist < currentDist) 
				{
					distMap.set(x, y, newDist);
					modif = true;
				}
			}
		}
		
		this.fireProgressChanged(this, sizeY, sizeY);
	}
}