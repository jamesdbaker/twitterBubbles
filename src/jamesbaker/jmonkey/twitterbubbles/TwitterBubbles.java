package jamesbaker.jmonkey.twitterbubbles;

import java.io.File;
import java.util.Random;
import java.util.Stack;

import twitter4j.FilterQuery;
import twitter4j.GeoLocation;
import twitter4j.StallWarning;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.StatusListener;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;

import com.jme3.app.SimpleApplication;
import com.jme3.app.state.VideoRecorderAppState;
import com.jme3.collision.CollisionResults;
import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Sphere;
import com.jme3.scene.shape.Sphere.TextureMode;

public class TwitterBubbles extends SimpleApplication implements StatusListener {
	// Configuration parameters
	private final float CEILING = 6;
	private final float START_FADE = 4;

	private final static float BUBBLE_SIZE = 0.02f;
	private final float BUBBLE_SPEED = 0.1f;
	private final float BUBBLE_COLOR_R = 1;
	private final float BUBBLE_COLOR_G = 1;
	private final float BUBBLE_COLOR_B = 1;

	private final float POSITIVE_COLOR_R = 0;
	private final float POSITIVE_COLOR_G = 1;
	private final float POSITIVE_COLOR_B = 0;

	private final float NEGATIVE_COLOR_R = 1;
	private final float NEGATIVE_COLOR_G = 0;
	private final float NEGATIVE_COLOR_B = 0;

	private final float BBOX_N = 58.67f;
	private final float BBOX_S = 49.95f;
	private final float BBOX_E = 1.75f;
	private final float BBOX_W = -8.2f;

	private final ColorRGBA FLOOR_COLOR = new ColorRGBA(0.5f, 0.5f, 0.5f, 1);

	private final boolean SHOW_STATS = false;
	private final boolean SHOW_LOC_INFO = false;

	private final float SPARKS_LOW_LIFE = 2f;
	private final float SPARKS_HIGH_LIFE = 4f;
	private final float SPARKS_RATE = 500f;
	private final float SPARKS_EMITTER_LIFE = 5f;
	private final ColorRGBA SPARKS_COLOR_START = new ColorRGBA(1, 0.84f, 0, 1);
	private final ColorRGBA SPARKS_COLOR_END = new ColorRGBA(0, 0, 0, 0.5f);
	
	private final String RECORD_VIDEO_FILE = "twitterBubble.avi";

	// Twitter Variables
	private Stack<Status> tweets = new Stack<Status>();
	private TwitterStream twitterStream;

	// jMonkey Variables
	private static final Sphere bubble;

	static {
		bubble = new Sphere(16, 16, BUBBLE_SIZE);
		bubble.setTextureMode(TextureMode.Projected);
	}

	Node bubbles;
	Material bubbleMaterial;
	Material positiveGlow;
	Material negativeGlow;
	Geometry floor;
	Node emitters;

	Node tweetDetails;

	boolean showLocInfo = SHOW_LOC_INFO;
	BitmapText currentLocation;
	BitmapText currentTarget;

	FilterPostProcessor fpp;
	BloomFilter bloom;

	// Twitter Listener Functions
	public void onStatus(Status status) {
		if (status.getGeoLocation() != null)
			tweets.add(status);
	}

	public void onException(Exception ex) {
		ex.printStackTrace();
	}

	public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {
	}

	public void onTrackLimitationNotice(int numberOfLimitedStatuses) {
	}

	public void onScrubGeo(long arg0, long arg1) {
	}

	public void onStallWarning(StallWarning arg0) {
	}

	// Main App

	public static void main(String[] args) {
		TwitterBubbles app = new TwitterBubbles();
		app.start();
	}

	@Override
	public void simpleInitApp() {
		//Record video
		if(RECORD_VIDEO_FILE != null){
			stateManager.attach(new VideoRecorderAppState(new File(RECORD_VIDEO_FILE)));
		}

		// Show/Hide stats
		setDisplayFps(SHOW_STATS);
		setDisplayStatView(SHOW_STATS);

		// Calculate centre of bounding box
		float centreZ = -(BBOX_S + ((BBOX_N - BBOX_S) / 2));
		float centreX = BBOX_W + ((BBOX_E - BBOX_W) / 2);

		// Set up camera
		cam.setLocation(new Vector3f(centreX, 10, -(BBOX_S - 5)));
		cam.lookAt(new Vector3f(centreX, 0, centreZ), Vector3f.UNIT_Y);
		flyCam.setMoveSpeed(2.5f);

		// Initialize bubble material
		bubbleMaterial = new Material(assetManager,
				"Common/MatDefs/Misc/Unshaded.j3md");
		bubbleMaterial.setColor("Color", new ColorRGBA(BUBBLE_COLOR_R,
				BUBBLE_COLOR_G, BUBBLE_COLOR_B, 1));

		// Initialize glow materials
		positiveGlow = bubbleMaterial.clone();
		negativeGlow = bubbleMaterial.clone();
		positiveGlow.setColor("GlowColor", new ColorRGBA(POSITIVE_COLOR_R,
				POSITIVE_COLOR_G, POSITIVE_COLOR_B, 1));
		negativeGlow.setColor("GlowColor", new ColorRGBA(NEGATIVE_COLOR_R,
				NEGATIVE_COLOR_G, NEGATIVE_COLOR_B, 1));

		// Add glow post processor
		fpp = new FilterPostProcessor(assetManager);
		bloom = new BloomFilter(BloomFilter.GlowMode.Objects);
		fpp.addFilter(bloom);
		viewPort.addProcessor(fpp);

		// Initialize nodes
		bubbles = new Node("tweetBubbles");
		bubbles.setQueueBucket(Bucket.Transparent);
		rootNode.attachChild(bubbles);

		tweetDetails = new Node("tweetDetails");

		emitters = new Node("sparkEmitters");
		rootNode.attachChild(emitters);

		// Create floor
		Box floorBox = new Box(FastMath.abs(BBOX_E - BBOX_W) / 2, 0,
				FastMath.abs(BBOX_N - BBOX_S) / 2);
		floor = new Geometry("floor", floorBox);
		Material matFloor = new Material(assetManager,
				"Common/MatDefs/Misc/Unshaded.j3md");
		matFloor.setTexture("ColorMap", assetManager.loadTexture("uk.png"));
		matFloor.setColor("Color", FLOOR_COLOR);
		floor.setMaterial(matFloor);
		floor.setLocalTranslation(new Vector3f(centreX, 0, centreZ));
		rootNode.attachChild(floor);

		// Set up geo query
		FilterQuery query = new FilterQuery();
		double[][] bbox = { { BBOX_W, BBOX_S }, { BBOX_E, BBOX_N } };
		query.locations(bbox);

		// Start listening for Tweets
		twitterStream = new TwitterStreamFactory().getInstance();
		twitterStream.addListener(this);
		twitterStream.filter(query);

		// Add selection crosshair
		guiNode.detachAllChildren();
		guiFont = assetManager.loadFont("Interface/Fonts/Default.fnt");
		BitmapText ch = new BitmapText(guiFont, false);
		ch.setSize(guiFont.getCharSet().getRenderedSize() * 2);
		ch.setText("+");
		ch.setLocalTranslation(settings.getWidth() / 2
				- guiFont.getCharSet().getRenderedSize() / 3 * 2,
				settings.getHeight() / 2 + ch.getLineHeight() / 2, 0);
		guiNode.attachChild(ch);

		// Initialize current location
		currentLocation = new BitmapText(guiFont, false);
		currentLocation.setSize(guiFont.getCharSet().getRenderedSize());

		if (showLocInfo)
			guiNode.attachChild(currentLocation);

		// Initialize current target
		currentTarget = new BitmapText(guiFont, false);
		currentTarget.setSize(guiFont.getCharSet().getRenderedSize());

		if (showLocInfo)
			guiNode.attachChild(currentTarget);

		// Add tweet details
		guiNode.attachChild(tweetDetails);

		// Add click listener
		inputManager.addMapping("tweetClick", new MouseButtonTrigger(
				MouseInput.BUTTON_LEFT));
		inputManager.addMapping("toggleLocInfo", new KeyTrigger(KeyInput.KEY_L));
		inputManager.addMapping("presetCamera0", new KeyTrigger(KeyInput.KEY_0));
		inputManager.addMapping("presetCamera9", new KeyTrigger(KeyInput.KEY_9));
		inputManager.addMapping("presetCamera8", new KeyTrigger(KeyInput.KEY_8));
		
		inputManager.addListener(listener, "tweetClick", "toggleLocInfo", "presetCamera0", "presetCamera9", "presetCamera8");
	}

	@Override
	public void simpleUpdate(float tpf) {
		if (showLocInfo) {
			// Display current position
			currentLocation.setText(String.format(
					"Current Location: %.2f, %.2f", cam.getLocation().x,
					-cam.getLocation().z));
			currentLocation.setLocalTranslation(settings.getWidth()
					- currentLocation.getLineWidth() - 5, guiFont.getCharSet()
					.getLineHeight() + 5, 0);

			// Display current target
			Ray ray = new Ray(cam.getLocation(), cam.getDirection());
			CollisionResults results = new CollisionResults();
			floor.collideWith(ray, results);

			if (results.size() > 0) {
				Vector3f target = results.getClosestCollision()
						.getContactPoint();

				currentTarget.setText(String.format(
						"Current Target: %.2f, %.2f", target.x, -target.z));
				currentTarget.setLocalTranslation(settings.getWidth()
						- currentTarget.getLineWidth() - 5, 2 * guiFont
						.getCharSet().getLineHeight(), 0);
			} else {
				currentTarget.setText("Current Target: -");
				currentTarget.setLocalTranslation(settings.getWidth()
						- currentTarget.getLineWidth() - 5, 2 * guiFont
						.getCharSet().getLineHeight() + 5, 0);
			}
		}

		// Add Tweets
		while (!tweets.isEmpty()) {
			Status tweet = tweets.pop();
			addTweetBubble(tweet);
		}

		// Move all bubbles up
		bubbles.move(0, BUBBLE_SPEED * tpf, 0);

		// Fade out and remove bubbles that have hit the ceiling
		float fadeRange = CEILING - START_FADE;
		for (Spatial child : bubbles.getChildren()) {
			float y = child.getWorldTranslation().y;
			if (y >= CEILING) {
				child.removeFromParent();
			} else if (y >= START_FADE) {
				float opacity = 1 - (y - START_FADE) / fadeRange;

				Geometry g = (Geometry) child;

				Material m = g.getMaterial().clone();
				m.setColor("Color", new ColorRGBA(BUBBLE_COLOR_R,
						BUBBLE_COLOR_G, BUBBLE_COLOR_B, opacity));
				if ((Integer) g.getUserData("sentiment") < 0) {
					m.setColor("GlowColor", new ColorRGBA(NEGATIVE_COLOR_R,
							NEGATIVE_COLOR_G, NEGATIVE_COLOR_B, opacity));
				} else if ((Integer) g.getUserData("sentiment") > 0) {
					m.setColor("GlowColor", new ColorRGBA(POSITIVE_COLOR_R,
							POSITIVE_COLOR_G, POSITIVE_COLOR_B, opacity));
				}
				m.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
				g.setMaterial(m);
			}
		}

		// Remove emitters as they reach the end of their life
		for (Spatial emitter : emitters.getChildren()) {
			float remaining = emitter.getUserData("remainingLife");
			remaining -= tpf;

			if (remaining < 0) {
				ParticleEmitter pe = (ParticleEmitter) emitter;
				pe.setParticlesPerSec(0);
			}

			emitter.setUserData("remainingLife", remaining);

			if (remaining < -SPARKS_HIGH_LIFE) {
				emitter.removeFromParent();
				continue;
			}

		}
	}

	@Override
	public void destroy() {
		super.destroy();

		try {
			twitterStream.shutdown();
		} catch (Exception e) {
		}
	}

	private ActionListener listener = new ActionListener() {
		public void onAction(String name, boolean keyPressed, float tpf) {
			if (name.equals("tweetClick") && !keyPressed) {
				// Get clicked tweet
				Ray ray = new Ray(cam.getLocation(), cam.getDirection());
				CollisionResults results = new CollisionResults();
				bubbles.collideWith(ray, results);

				if (results.size() > 0) {
					// Display selected tweet on screen
					Geometry g = results.getClosestCollision().getGeometry();

					String tweetText = g.getUserData("text");
					String tweetUser = g.getUserData("user");

					tweetDetails.detachAllChildren();

					BitmapText user = new BitmapText(guiFont, false);
					user.setSize(guiFont.getCharSet().getRenderedSize());
					user.setText("@" + tweetUser);
					user.setLocalTranslation(5, settings.getHeight(), 0);
					tweetDetails.attachChild(user);

					BitmapText message = new BitmapText(guiFont, false);
					message.setSize(guiFont.getCharSet().getRenderedSize());
					message.setText(tweetText);
					message.setLocalTranslation(5, settings.getHeight()
							- guiFont.getCharSet().getLineHeight(), 0);
					tweetDetails.attachChild(message);
				} else {
					// No tweet clicked, hide details
					tweetDetails.detachAllChildren();
				}
			} else if (name.equals("toggleLocInfo") && !keyPressed) {
				showLocInfo = !showLocInfo;

				if (showLocInfo) {
					// Add location information to GUI
					guiNode.attachChild(currentLocation);
					guiNode.attachChild(currentTarget);
				} else {
					// Remove location information from GUI
					currentLocation.removeFromParent();
					currentTarget.removeFromParent();
				}
			} else if (name.equals("presetCamera0") && !keyPressed){
				//At centre from south (default)
				float centreZ = -(BBOX_S + ((BBOX_N - BBOX_S) / 2));
				float centreX = BBOX_W + ((BBOX_E - BBOX_W) / 2);

				cam.setLocation(new Vector3f(centreX, 5, -(BBOX_S - 2)));
				cam.lookAt(new Vector3f(centreX, 0, centreZ), Vector3f.UNIT_Y);
			} else if (name.equals("presetCamera9") && !keyPressed){
				//Directly down over centre
				float centreZ = -(BBOX_S + ((BBOX_N - BBOX_S) / 2));
				float centreX = BBOX_W + ((BBOX_E - BBOX_W) / 2);

				cam.setLocation(new Vector3f(centreX, 5, centreZ));
				cam.lookAt(new Vector3f(centreX, 0, centreZ), Vector3f.UNIT_Y);
			} else if (name.equals("presetCamera8") && !keyPressed){
				//At centre from north
				float centreZ = -(BBOX_S + ((BBOX_N - BBOX_S) / 2));
				float centreX = BBOX_W + ((BBOX_E - BBOX_W) / 2);

				cam.setLocation(new Vector3f(centreX, 5, -(BBOX_N + 2)));
				cam.lookAt(new Vector3f(centreX, 0, centreZ), Vector3f.UNIT_Y);
			}
		}
	};

	public void addTweetBubble(Status status) {
		GeoLocation geo = status.getGeoLocation();

		// Create new bubble and attach to bubbles node
		Geometry tweetBubble = new Geometry("tweetBubble", bubble);

		tweetBubble.setUserData("text", status.getText());
		tweetBubble.setUserData("user", status.getUser().getScreenName());

		int sentiment = getSentiment(status.getText());
		tweetBubble.setUserData("sentiment", sentiment);
		if (sentiment > 0) {
			tweetBubble.setMaterial(positiveGlow);
		} else if (sentiment < 0) {
			tweetBubble.setMaterial(negativeGlow);
		} else {
			tweetBubble.setMaterial(bubbleMaterial);
		}

		tweetBubble.setLocalTranslation((float) geo.getLongitude(),
				-bubbles.getLocalTranslation().y, -(float) geo.getLatitude());

		tweetBubble.setQueueBucket(Bucket.Transparent);

		bubbles.attachChild(tweetBubble);

		// Emit sparks where we've created the bubble
		ParticleEmitter tweetSparks = new ParticleEmitter("RoundSpark",
				ParticleMesh.Type.Triangle, (int)FastMath.ceil(SPARKS_RATE*SPARKS_HIGH_LIFE));
		tweetSparks.setStartColor(SPARKS_COLOR_START);
		tweetSparks.setEndColor(SPARKS_COLOR_END);
		tweetSparks.setStartSize(0.05f);
		tweetSparks.setEndSize(0.1f);
		tweetSparks.setParticlesPerSec(SPARKS_RATE);
		tweetSparks.setGravity(0, -1, 0);
		tweetSparks.getParticleInfluencer().setInitialVelocity(
				new Vector3f(0, 1, 0));
		tweetSparks.setLowLife(SPARKS_LOW_LIFE);
		tweetSparks.setHighLife(SPARKS_HIGH_LIFE);
		tweetSparks.setImagesX(1);
		tweetSparks.setImagesY(1);

		Material mat = new Material(assetManager,
				"Common/MatDefs/Misc/Particle.j3md");
		mat.setTexture("Texture",
				assetManager.loadTexture("Effects/Explosion/roundspark.png"));
		tweetSparks.setMaterial(mat);

		emitters.attachChild(tweetSparks);
		tweetSparks.setLocalTranslation((float) geo.getLongitude(), 0,
				-(float) geo.getLatitude());
		tweetSparks.setUserData("remainingLife", SPARKS_EMITTER_LIFE);

	}

	public int getSentiment(String message) {
		Random r = FastMath.rand;
		return r.nextInt(3) - 1;
	}

}
