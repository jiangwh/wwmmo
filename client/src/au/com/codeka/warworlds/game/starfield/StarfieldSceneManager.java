package au.com.codeka.warworlds.game.starfield;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

import org.andengine.engine.camera.hud.HUD;
import org.andengine.entity.Entity;
import org.andengine.entity.scene.Scene;
import org.andengine.entity.sprite.Sprite;
import org.andengine.input.touch.TouchEvent;
import org.andengine.opengl.font.Font;
import org.andengine.opengl.font.FontFactory;
import org.andengine.opengl.texture.TextureOptions;
import org.andengine.opengl.texture.atlas.ITextureAtlas.ITextureAtlasStateListener;
import org.andengine.opengl.texture.atlas.bitmap.BitmapTextureAtlas;
import org.andengine.opengl.texture.atlas.bitmap.BitmapTextureAtlasTextureRegionFactory;
import org.andengine.opengl.texture.atlas.bitmap.BuildableBitmapTextureAtlas;
import org.andengine.opengl.texture.atlas.bitmap.source.IBitmapTextureAtlasSource;
import org.andengine.opengl.texture.atlas.buildable.builder.BlackPawnTextureAtlasBuilder;
import org.andengine.opengl.texture.atlas.buildable.builder.ITextureAtlasBuilder.TextureAtlasBuilderException;
import org.andengine.opengl.texture.region.ITextureRegion;
import org.andengine.opengl.texture.region.TiledTextureRegion;
import org.joda.time.DateTime;

import android.graphics.Color;
import android.graphics.Typeface;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import au.com.codeka.common.Log;
import au.com.codeka.common.Pair;
import au.com.codeka.common.PointCloud;
import au.com.codeka.common.Vector2;
import au.com.codeka.common.Voronoi;
import au.com.codeka.common.model.BaseColony;
import au.com.codeka.common.model.BaseFleet;
import au.com.codeka.common.model.BaseStar;
import au.com.codeka.warworlds.eventbus.EventBus;
import au.com.codeka.warworlds.eventbus.EventHandler;
import au.com.codeka.warworlds.model.BuildManager;
import au.com.codeka.warworlds.model.Colony;
import au.com.codeka.warworlds.model.Empire;
import au.com.codeka.warworlds.model.EmpireManager;
import au.com.codeka.warworlds.model.EmpireShieldManager;
import au.com.codeka.warworlds.model.Fleet;
import au.com.codeka.warworlds.model.MyEmpire;
import au.com.codeka.warworlds.model.Sector;
import au.com.codeka.warworlds.model.SectorManager;
import au.com.codeka.warworlds.model.Star;
import au.com.codeka.warworlds.model.StarManager;

/**
 * \c SurfaceView that displays the starfield. You can scroll around, tap on stars to bring
 * up their details and so on.
 */
public class StarfieldSceneManager extends SectorSceneManager {
    private static final Log log = new Log("StarfieldSceneManager");

    public static final EventBus eventBus = new EventBus();

    private BaseStar mHqStar;
    private boolean mHasScrolled;

    private boolean mWasDragging;

    private Font mFont;
    private BitmapTextureAtlas mStarTextureAtlas;
    private TiledTextureRegion mBigStarTextureRegion;
    private TiledTextureRegion mNormalStarTextureRegion;

    private BuildableBitmapTextureAtlas mIconTextureAtlas;
    private ITextureRegion mArrowIconTextureRegion;

    private BuildableBitmapTextureAtlas mFleetSpriteTextureAtlas;
    private HashMap<String, ITextureRegion> mFleetSpriteTextures;

    private BitmapTextureAtlas mBackgroundGasTextureAtlas;
    private TiledTextureRegion mBackgroundGasTextureRegion;
    private BitmapTextureAtlas mBackgroundStarsTextureAtlas;
    private TiledTextureRegion mBackgroundStarsTextureRegion;

    private boolean isBackgroundVisible = true;
    private float backgroundZoomAlpha = 1.0f;
    private boolean isTacticalVisible = false;
    private float tacticalZoomAlpha = 0.0f;

    public StarfieldSceneManager(BaseStarfieldActivity activity) {
        super(activity);
    }

    @Override
    public void onLoadResources() {
        mStarTextureAtlas = new BitmapTextureAtlas(mActivity.getTextureManager(), 256, 384,
                TextureOptions.BILINEAR_PREMULTIPLYALPHA);
        mStarTextureAtlas.setTextureAtlasStateListener(new ITextureAtlasStateListener.DebugTextureAtlasStateListener<IBitmapTextureAtlasSource>());

        mNormalStarTextureRegion = BitmapTextureAtlasTextureRegionFactory.createTiledFromAsset(mStarTextureAtlas, mActivity,
                "stars/stars_small.png", 0, 0, 4, 6);
        mBigStarTextureRegion = BitmapTextureAtlasTextureRegionFactory.createTiledFromAsset(mStarTextureAtlas, mActivity,
                "stars/stars_small.png", 0, 0, 2, 3);

        mBackgroundGasTextureAtlas = new BitmapTextureAtlas(mActivity.getTextureManager(), 512, 512,
                TextureOptions.BILINEAR_PREMULTIPLYALPHA);
        mBackgroundGasTextureAtlas.setTextureAtlasStateListener(new ITextureAtlasStateListener.DebugTextureAtlasStateListener<IBitmapTextureAtlasSource>());

        mBackgroundGasTextureRegion = BitmapTextureAtlasTextureRegionFactory.createTiledFromAsset(mBackgroundGasTextureAtlas,
                mActivity, "decoration/gas.png", 0, 0, 4, 4);
        mBackgroundStarsTextureAtlas = new BitmapTextureAtlas(mActivity.getTextureManager(), 512, 512,
                TextureOptions.BILINEAR_PREMULTIPLYALPHA);
        mBackgroundStarsTextureRegion = BitmapTextureAtlasTextureRegionFactory.createTiledFromAsset(mBackgroundStarsTextureAtlas,
                mActivity, "decoration/starfield.png", 0, 0, 4, 4);

        mIconTextureAtlas = new BuildableBitmapTextureAtlas(mActivity.getTextureManager(), 256, 256,
                TextureOptions.BILINEAR_PREMULTIPLYALPHA);
        mIconTextureAtlas.setTextureAtlasStateListener(new ITextureAtlasStateListener.DebugTextureAtlasStateListener<IBitmapTextureAtlasSource>());

        mArrowIconTextureRegion = BitmapTextureAtlasTextureRegionFactory.createFromAsset(mIconTextureAtlas, mActivity, "img/arrow.png");

        mFleetSpriteTextureAtlas = new BuildableBitmapTextureAtlas(mActivity.getTextureManager(), 256, 256,
                TextureOptions.BILINEAR_PREMULTIPLYALPHA);
        mFleetSpriteTextureAtlas.setTextureAtlasStateListener(new ITextureAtlasStateListener.DebugTextureAtlasStateListener<IBitmapTextureAtlasSource>());

        mFleetSpriteTextures = new HashMap<String, ITextureRegion>();
        mFleetSpriteTextures.put("ship.fighter", BitmapTextureAtlasTextureRegionFactory.createFromAsset(mFleetSpriteTextureAtlas, mActivity, "spritesheets/ship.fighter.png"));
        mFleetSpriteTextures.put("ship.scout", BitmapTextureAtlasTextureRegionFactory.createFromAsset(mFleetSpriteTextureAtlas, mActivity, "spritesheets/ship.scout.png"));
        mFleetSpriteTextures.put("ship.colony", BitmapTextureAtlasTextureRegionFactory.createFromAsset(mFleetSpriteTextureAtlas, mActivity, "spritesheets/ship.colony.png"));
        mFleetSpriteTextures.put("ship.troopcarrier", BitmapTextureAtlasTextureRegionFactory.createFromAsset(mFleetSpriteTextureAtlas, mActivity, "spritesheets/ship.troopcarrier.png"));
        mFleetSpriteTextures.put("ship.wormhole-generator", BitmapTextureAtlasTextureRegionFactory.createFromAsset(mFleetSpriteTextureAtlas, mActivity, "spritesheets/ship.wormhole-generator.png"));
        mFleetSpriteTextures.put("ship.upgrade.boost", BitmapTextureAtlasTextureRegionFactory.createTiledFromAsset(mFleetSpriteTextureAtlas, mActivity, "spritesheets/ship.upgrade.boost.png", 2, 1));

        mActivity.getShaderProgramManager().loadShaderProgram(RadarIndicatorEntity.getShaderProgram());
        mActivity.getTextureManager().loadTexture(mStarTextureAtlas);
        mActivity.getTextureManager().loadTexture(mBackgroundGasTextureAtlas);
        mActivity.getTextureManager().loadTexture(mBackgroundStarsTextureAtlas);

        try {
            BlackPawnTextureAtlasBuilder<IBitmapTextureAtlasSource, BitmapTextureAtlas> builder =
                    new BlackPawnTextureAtlasBuilder<IBitmapTextureAtlasSource, BitmapTextureAtlas>(1, 1, 1);
            mIconTextureAtlas.build(builder);
            mIconTextureAtlas.load();

            mFleetSpriteTextureAtlas.build(builder);
            mFleetSpriteTextureAtlas.load();
        } catch (TextureAtlasBuilderException e) {
            log.error("Error building texture atlas.", e);
        }

        mFont = FontFactory.create(mActivity.getFontManager(), mActivity.getTextureManager(), 256, 256,
                                   Typeface.create(Typeface.DEFAULT, Typeface.NORMAL), 16, true, Color.WHITE);
        mFont.load();

    }

    @Override
    protected void onStart() {
        super.onStart();

        StarManager.eventBus.register(mEventHandler);
        EmpireManager.eventBus.register(mEventHandler);

        MyEmpire myEmpire = EmpireManager.i.getEmpire();
        if (myEmpire != null) {
            BaseStar homeStar = myEmpire.getHomeStar();
            int numHqs = BuildManager.getInstance().getTotalBuildingsInEmpire("hq");
            if (numHqs > 0) {
                mHqStar = homeStar;
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        StarManager.eventBus.unregister(mEventHandler);
        EmpireManager.eventBus.unregister(mEventHandler);
    }

    public Font getFont() {
        return mFont;
    }

    public ITextureRegion getSpriteTexture(String spriteName) {
        return mFleetSpriteTextures.get(spriteName);
    }

    public ITextureRegion getArrowTexture() {
        return mArrowIconTextureRegion;
    }

    @Override
    public void scrollTo(final long sectorX, final long sectorY,
            final float offsetX, final float offsetY) {
        mHasScrolled = true;
        super.scrollTo(sectorX, sectorY, offsetX, offsetY);
    }

    public Star getSelectedStar() {
        return null;
    }

    @Override
    protected void refreshScene(StarfieldScene scene) {
        if (!mHasScrolled) {
            // if you haven't scrolled yet, then don't even think about refreshing the
            // scene... it's a waste of time!
            log.debug("We haven't scrolled yet, not drawing the scene.");
            return;
        }

        if (mActivity.getEngine() == null) {
            // if the engine hasn't been created yet, we can't really do anything.
            return;
        }

        final List<Pair<Long, Long>> missingSectors = drawScene(scene);
        if (missingSectors != null) {
            SectorManager.i.refreshSectors(missingSectors, false);
        }

        scene.refreshSelectionIndicator();
        eventBus.publish(new SceneUpdatedEvent(scene));
    }

    @Override
    protected int getDesiredSectorRadius() {
        return isTacticalVisible ? 2 : 1;
    }

    @Override
    protected void refreshHud(HUD hud) {
        MyEmpire myEmpire = EmpireManager.i.getEmpire();
        if (myEmpire != null && myEmpire.getHomeStar() != null) {
            // if you have a HQ, it'll be on your home star.
            if (BuildManager.getInstance().getTotalBuildingsInEmpire("hq") > 0) {
                hud.attachChild(new HqEntity(this, myEmpire.getHomeStar(), mActivity.getCamera(),
                        mActivity.getVertexBufferObjectManager()));
            }
        }
    }

    @Override
    protected void updateZoomFactor(float zoomFactor) {
        super.updateZoomFactor(zoomFactor);
        boolean wasTacticalVisible = isTacticalVisible;
        boolean wasBackgroundVisible = isBackgroundVisible;

        // we fade out the background between 0.55 and 0.50, it should be totally invisible < 0.50
        // and totally opaque for >= 0.55
        if (zoomFactor < 0.5f && isBackgroundVisible) {
            isBackgroundVisible = false;
            // we need to make the background as invisible
            mActivity.runOnUpdateThread(updateBackgroundRunnable);
        } else if (zoomFactor >= 0.5f && !isBackgroundVisible) {
            isBackgroundVisible = true;
            // we need to make the background as visible
            mActivity.runOnUpdateThread(updateBackgroundRunnable);
        }
        if (zoomFactor >= 0.5f && zoomFactor < 0.55f) {
            // between 0.5 and 0.55 we need to fade the background in
            backgroundZoomAlpha = (zoomFactor - 0.5f) * 20.0f; // make it in the range 0...1
            mActivity.runOnUpdateThread(updateBackgroundRunnable);
        }

        // similarly, we fade IN the tactical view as you zoom out. It starts fading in a bit sooner
        // than the background fades out, and fades slower, too.
        if (zoomFactor >= 0.6f && isTacticalVisible) {
            isTacticalVisible = false;
            tacticalZoomAlpha = 0.0f;
            mActivity.runOnUpdateThread(updateTacticalRunnable);
        } else if (zoomFactor < 0.4f && !isTacticalVisible ) {
            isTacticalVisible = true;
            tacticalZoomAlpha = 1.0f;
            mActivity.runOnUpdateThread(updateTacticalRunnable);
        }
        if (zoomFactor >= 0.4f && zoomFactor < 0.6f) {
            isTacticalVisible = true;
            tacticalZoomAlpha = 1.0f - ((zoomFactor - 0.4f) * 5.0f); // make it 1...0
            mActivity.runOnUpdateThread(updateTacticalRunnable);
        }

        if (wasTacticalVisible != isTacticalVisible
            || wasBackgroundVisible != isBackgroundVisible) {
            // If the tactical view or background has gone from visible -> invisible (or vice
            // versa), then we'll need to redraw the scene.
            queueRefreshScene();
        }
    }

    /** Updates the background entities with the current zoom alpha on the update thread. */
    private final Runnable updateBackgroundRunnable = new Runnable() {
        @Override
        public void run() {
            StarfieldScene scene = getScene();
            if (scene == null) {
                return;
            }
            List<Entity> backgroundEntities = scene.getBackgroundEntities();
            if (backgroundEntities == null) {
                return;
            }
            for (Entity entity : backgroundEntities) {
                entity.setVisible(isBackgroundVisible);
                entity.setAlpha(backgroundZoomAlpha);
                entity.setColor(backgroundZoomAlpha, backgroundZoomAlpha,
                    backgroundZoomAlpha);
            }
        }
    };

    /** Updates the tactical entities with the current zoom alpha on the update thread. */
    private final Runnable updateTacticalRunnable = new Runnable() {
        @Override
        public void run() {
            StarfieldScene scene = getScene();
            if (scene == null) {
                return;
            }
            SparseArray<TacticalControlField> controlFields = scene.getControlFields();
            if (controlFields == null) {
                return;
            }
            for (int i = 0; i < controlFields.size(); i++) {
                controlFields.valueAt(i).updateAlpha(isTacticalVisible, tacticalZoomAlpha);
            }
        }
    };

    private List<Pair<Long, Long>> drawScene(StarfieldScene scene) {
        List<Pair<Long, Long>> missingSectors = null;

        for(int y = -scene.getSectorRadius(); y <= scene.getSectorRadius(); y++) {
            for(int x = -scene.getSectorRadius(); x <= scene.getSectorRadius(); x++) {
                long sX = scene.getSectorX() + x;
                long sY = scene.getSectorY() + y;
                Sector sector = SectorManager.i.getSector(sX, sY);
                if (sector == null) {
                    if (missingSectors == null) {
                        missingSectors = new ArrayList<Pair<Long, Long>>();
                    }
                    missingSectors.add(new Pair<Long, Long>(sX, sY));
                    continue;
                }

                int sx = (int)(x * Sector.SECTOR_SIZE);
                int sy = -(int)(y * Sector.SECTOR_SIZE);
                drawBackground(scene, sector, sx, sy);
            }
        }

        addTacticalView(scene);

        for (int y = -scene.getSectorRadius(); y <= scene.getSectorRadius(); y++) {
            for(int x = -scene.getSectorRadius(); x <= scene.getSectorRadius(); x++) {
                long sX = scene.getSectorX() + x;
                long sY = scene.getSectorY() + y;

                Sector sector = SectorManager.i.getSector(sX, sY);
                if (sector == null) {
                    continue;
                }

                int sx = (int)(x * Sector.SECTOR_SIZE);
                int sy = -(int)(y * Sector.SECTOR_SIZE);
                addSector(scene, sx, sy, sector);
            }
        }

        return missingSectors;
    }

    private void drawBackground(StarfieldScene scene, Sector sector, int sx, int sy) {
        Random r = new Random(sector.getX() ^ (long)(sector.getY() * 48647563));
        final int STAR_SIZE = 256;
        for (int y = 0; y < Sector.SECTOR_SIZE / STAR_SIZE; y++) {
            for (int x = 0; x < Sector.SECTOR_SIZE / STAR_SIZE; x++) {
                Sprite bgSprite = new Sprite(
                        (float) (sx + (x * STAR_SIZE)),
                        (float) (sy + (y * STAR_SIZE)),
                        STAR_SIZE, STAR_SIZE,
                        mBackgroundStarsTextureRegion.getTextureRegion(r.nextInt(16)),
                        mActivity.getVertexBufferObjectManager());
                setBackgroundEntityZoomFactor(bgSprite);
                scene.attachBackground(bgSprite);
            }
        }

        final int GAS_SIZE = 512;
        for (int i = 0; i < 10; i++) {
            float x = r.nextInt(Sector.SECTOR_SIZE + (GAS_SIZE / 4)) - (GAS_SIZE / 8);
            float y = r.nextInt(Sector.SECTOR_SIZE + (GAS_SIZE / 4)) - (GAS_SIZE / 8);

            Sprite bgSprite = new Sprite(
                    (sx + x) - (GAS_SIZE / 2.0f),
                    (sy + y) - (GAS_SIZE / 2.0f),
                    GAS_SIZE, GAS_SIZE,
                    mBackgroundGasTextureRegion.getTextureRegion(r.nextInt(14)),
                    mActivity.getVertexBufferObjectManager());
            setBackgroundEntityZoomFactor(bgSprite);
            scene.attachBackground(bgSprite);
        }
    }

    private void setBackgroundEntityZoomFactor(Sprite bgSprite) {
        if (backgroundZoomAlpha <= 0.0f) {
            bgSprite.setVisible(false);
        } else if (backgroundZoomAlpha >= 1.0f) {
            // do nothing
        } else {
            bgSprite.setAlpha(backgroundZoomAlpha);
            bgSprite.setColor(backgroundZoomAlpha, backgroundZoomAlpha, backgroundZoomAlpha);
        }
    }

    /**
     * Draws a sector, which is a 1024x1024 area of stars.
     */
    private void addSector(StarfieldScene scene, int offsetX, int offsetY, Sector sector) {
        for(BaseStar star : sector.getStars()) {
            addStar(scene, (Star) star, offsetX, offsetY);
        }
        for (BaseStar star : sector.getStars()) {
            for (BaseFleet fleet : star.getFleets()) {
                if (fleet.getState() == Fleet.State.MOVING) {
                    addMovingFleet(scene, (Fleet) fleet, (Star) star, offsetX, offsetY);
                }
            }
        }
    }

    /**
     * Draws a single star. Note that we draw all stars first, then the names of stars
     * after.
     */
    private void addStar(StarfieldScene scene, Star star, int x, int y) {
        x += star.getOffsetX();
        y += Sector.SECTOR_SIZE - star.getOffsetY();

        ITextureRegion textureRegion = null;
        if (star.getStarType().getInternalName().equals("neutron")) {
            textureRegion = mBigStarTextureRegion.getTextureRegion(0);
        } else if (star.getStarType().getInternalName().equals("wormhole")) {
            textureRegion = mBigStarTextureRegion.getTextureRegion(1);
        } else {
            int offset = 0;
            if (star.getStarType().getInternalName().equals("black-hole")) {
                offset = 8;
            } else if (star.getStarType().getInternalName().equals("blue")) {
                offset = 9;
            } else if (star.getStarType().getInternalName().equals("orange")) {
                offset = 12;
            } else if (star.getStarType().getInternalName().equals("red")) {
                offset = 13;
            } else if (star.getStarType().getInternalName().equals("white")) {
                offset = 16;
            } else if (star.getStarType().getInternalName().equals("yellow")) {
                offset = 17;
            } else if (star.getStarType().getInternalName().equals("marker")) {
                offset = 18;
            }
            textureRegion = mNormalStarTextureRegion.getTextureRegion(offset);
        }

        StarEntity starEntity = new StarEntity(this, star,
                                               (float) x, (float) y,
                                               textureRegion, mActivity.getVertexBufferObjectManager(),
                                               !isTacticalVisible, 1.0f - tacticalZoomAlpha);
        scene.registerTouchArea(starEntity.getTouchEntity());
        scene.attachChild(starEntity);
    }

    /**
     * Given a sector, returns the (x, y) coordinates (in view-space) of the origin of this
     * sector.
     */
    public Vector2 getSectorOffset(long sx, long sy) {
        return getSectorOffset(mSectorX, mSectorY, sx, sy);
    }

    public Vector2 getSectorOffset(long sectorX, long sectorY, long sx, long sy) {
        sx -= sectorX;
        sy -= sectorY;
        return Vector2.pool.borrow().reset((sx * Sector.SECTOR_SIZE),
                                           -(sy * Sector.SECTOR_SIZE));
    }

    /**
     * Draw a moving fleet as a line between the source and destination stars, with an icon
     * representing the current location of the fleet.
     */
    private void addMovingFleet(StarfieldScene scene, Fleet fleet, Star srcStar, int offsetX, int offsetY) {
        // we'll need to find the destination star
        Star destStar = SectorManager.i.findStar(fleet.getDestinationStarKey());
        if (destStar == null) {
            // the destination star isn't in one of the sectors we have in memory, we'll
            // just ignore this fleet (it's probably flying off the edge of the sector and our
            // little viewport won't see it anyway -- unless you've got a REALLY long-range
            // flight, maybe we can stop that from being possible).
            return;
        }

        Vector2 srcPoint = Vector2.pool.borrow().reset(offsetX, offsetY);
        srcPoint.x += srcStar.getOffsetX();
        srcPoint.y += Sector.SECTOR_SIZE - srcStar.getOffsetY();

        Vector2 destPoint = getSectorOffset(scene.getSectorX(), scene.getSectorY(),
            destStar.getSectorX(), destStar.getSectorY());
        destPoint.x += destStar.getOffsetX();
        destPoint.y += Sector.SECTOR_SIZE - destStar.getOffsetY();

        FleetEntity fleetEntity = new FleetEntity(this, srcPoint, destPoint, fleet, mActivity.getVertexBufferObjectManager());
        scene.registerTouchArea(fleetEntity.getTouchEntity());
        scene.attachChild(fleetEntity);
    }

    Collection<FleetEntity> getMovingFleets() {
        return this.getScene().getFleets().values();
    }

    private void addTacticalView(StarfieldScene scene) {
        if (!isTacticalVisible) {
            return;
        }
        ArrayList<Vector2> points = new ArrayList<Vector2>();
        SparseArray<List<Vector2>> empirePoints = new SparseArray<List<Vector2>>();

        for(int y = -scene.getSectorRadius(); y <= scene.getSectorRadius(); y++) {
            for(int x = -scene.getSectorRadius(); x <= scene.getSectorRadius(); x++) {
                long sX = scene.getSectorX() + x;
                long sY = scene.getSectorY() + y;

                Sector sector = SectorManager.i.getSector(sX, sY);
                if (sector == null) {
                    continue;
                }

                int sx = (int)(x * Sector.SECTOR_SIZE);
                int sy = -(int)(y * Sector.SECTOR_SIZE);

                for (BaseStar star : sector.getStars()) {
                    int starX = sx + star.getOffsetX();
                    int starY = sy + (Sector.SECTOR_SIZE - star.getOffsetY());
                    Vector2 pt = new Vector2((float) starX / Sector.SECTOR_SIZE, (float) starY / Sector.SECTOR_SIZE);

                    TreeSet<Integer> doneEmpires = new TreeSet<Integer>();
                    for (BaseColony c : star.getColonies()) {
                        Integer empireID = ((Colony) c).getEmpireID();
                        if (empireID == null) {
                            continue;
                        }
                        if (doneEmpires.contains(empireID)) {
                            continue;
                        }
                        doneEmpires.add(empireID);
                        List<Vector2> thisEmpirePoints = empirePoints.get(empireID);
                        if (thisEmpirePoints == null) {
                            thisEmpirePoints = new ArrayList<Vector2>();
                            empirePoints.put(empireID, thisEmpirePoints);
                        }
                        thisEmpirePoints.add(pt);
                    }
                    points.add(pt);
                }
            }
        }

        SparseArray<TacticalControlField> controlFields = new SparseArray<TacticalControlField>();
        PointCloud pointCloud = new PointCloud(points);
        Voronoi v = new Voronoi(pointCloud);

        for (int i = 0; i < empirePoints.size(); i++) {
            TacticalControlField cf = new TacticalControlField(pointCloud, v);

            List<Vector2> pts = empirePoints.valueAt(i);
            for (Vector2 pt : pts) {
                cf.addPointToControlField(pt);
            }

            int colour = Color.RED;
            int empireID = empirePoints.keyAt(i);
            Empire empire = EmpireManager.i.getEmpire(empireID);
            if (empire != null) {
                colour = EmpireShieldManager.i.getShieldColour(empire);
            } else {
                final int theEmpireID = empireID;
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        EmpireManager.i.refreshEmpire(theEmpireID);
                    }
                });
            }
            cf.addToScene(scene, getActivity().getVertexBufferObjectManager(), colour,
                tacticalZoomAlpha);
            controlFields.put(empireID, cf);
        }
        scene.setControlFields(controlFields);
    }

    @Override
    public boolean onSceneTouchEvent(Scene scene, TouchEvent touchEvent) {
        boolean handled = super.onSceneTouchEvent(scene, touchEvent);

        if (touchEvent.getAction() == TouchEvent.ACTION_DOWN) {
            mWasDragging = false;
        } else if (touchEvent.getAction() == TouchEvent.ACTION_UP) {
            if (!mWasDragging) {
                float tx = touchEvent.getX();
                float ty = touchEvent.getY();

                long sectorX = (long) (tx / Sector.SECTOR_SIZE) + mSectorX;
                long sectorY = (long) (ty / Sector.SECTOR_SIZE) + mSectorY;
                int offsetX = (int) (tx - (tx / Sector.SECTOR_SIZE));
                int offsetY = Sector.SECTOR_SIZE - (int) (ty - (ty / Sector.SECTOR_SIZE));
                while (offsetX < 0) {
                    sectorX --;
                    offsetX += Sector.SECTOR_SIZE;
                }
                while (offsetX > Sector.SECTOR_SIZE) {
                    sectorX ++;
                    offsetX -= Sector.SECTOR_SIZE;
                }
                while (offsetY < 0) {
                    sectorY --;
                    offsetY += Sector.SECTOR_SIZE;
                }
                while (offsetY > Sector.SECTOR_SIZE) {
                    sectorY ++;
                    offsetY -= Sector.SECTOR_SIZE;
                }

                getScene().selectNothing(sectorX, sectorY, offsetX, offsetY);
                handled = true;
            }
        }

        return handled;
    }

    @Override
    protected GestureDetector.OnGestureListener createGestureListener() {
        return new GestureListener();
    }

    @Override
    protected ScaleGestureDetector.OnScaleGestureListener createScaleGestureListener() {
        return new ScaleGestureListener();
    }

    /** The default gesture listener is just for scrolling around. */
    protected class GestureListener extends SectorSceneManager.GestureListener {
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
                float distanceY) {
            super.onScroll(e1, e2, distanceX, distanceY);

            // because we've navigating the map, we're no longer in the process of selecting a sprite.
            getScene().cancelSelect();
            mWasDragging = true;
            return true;
        }
    }

    /** The default scale gesture listener scales the view. */
    protected class ScaleGestureListener extends SectorSceneManager.ScaleGestureListener {
        @Override
        public boolean onScale (ScaleGestureDetector detector) {
            super.onScale(detector);

            // because we've navigating the map, we're no longer in the process of selecting a sprite.
            getScene().cancelSelect();
            mWasDragging = true;
            return true;
        }
    }

    private Object mEventHandler = new Object() {
        private String lastMyEmpireName;
        private DateTime lastMyEmpireShieldUpdateTime;

        /**
         * When a star is updated, if it's one of ours, then we'll want to redraw to make sure we
         * have the latest data (e.g. it might've been renamed)
         */
        @EventHandler
        public void onStarFetched(final Star s) {
            getActivity().runOnUpdateThread(new Runnable() {
                @Override
                public void run() {
                    StarfieldScene scene = getScene();
                    if (scene != null) {
                        scene.onStarFetched(s);
                    }
                }
            });
        }

        @EventHandler
        public void onEmpireUpdate(Empire empire) {
            MyEmpire myEmpire = EmpireManager.i.getEmpire();
            if (empire.getKey().equals(myEmpire.getKey())) {
                // If the player's empire changes, it might mean that the location of their HQ has
                // changed, so we'll want to make sure it's still correct.
                if (mHqStar != null) {
                    mHqStar = empire.getHomeStar();
                }

                // Only refresh the scene if something we actually care about has changed (such
                // as the player's name or the shield image). Otherwise, this gets fired for every
                // notification, for example, and we don't need to redraw the scene for that.
                if (lastMyEmpireName == null || !lastMyEmpireName.equals(empire.getDisplayName())
                      || lastMyEmpireShieldUpdateTime == null
                      || !lastMyEmpireShieldUpdateTime.equals(empire.getShieldLastUpdate())) {
                  lastMyEmpireName = empire.getDisplayName();
                  lastMyEmpireShieldUpdateTime = empire.getShieldLastUpdate();
                  queueRefreshScene();
                }

                return;
            }

            // If it's anyone but the player, then just refresh the scene.
            queueRefreshScene();
        }
    };

    public static class SpaceTapEvent {
        public long sectorX;
        public long sectorY;
        public int offsetX;
        public int offsetY;

        public SpaceTapEvent(long sectorX, long sectorY, int offsetX, int offsetY) {
            this.sectorX = sectorX;
            this.sectorY = sectorY;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }
    }

    public static class StarSelectedEvent {
        public Star star;

        public StarSelectedEvent(Star star) {
            this.star = star;
        }
    }

    public static class FleetSelectedEvent {
        public Fleet fleet;

        public FleetSelectedEvent(Fleet fleet) {
            this.fleet = fleet;
        }
    }

    public static class SceneUpdatedEvent {
        public StarfieldScene scene;

        public SceneUpdatedEvent(StarfieldScene scene) {
            this.scene = scene;
        }
    }
}
