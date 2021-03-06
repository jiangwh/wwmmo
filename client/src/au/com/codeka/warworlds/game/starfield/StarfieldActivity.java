package au.com.codeka.warworlds.game.starfield;

import java.util.Locale;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import au.com.codeka.common.Log;
import au.com.codeka.common.model.BaseColony;
import au.com.codeka.common.model.BaseFleet;
import au.com.codeka.common.model.BaseStar;
import au.com.codeka.common.model.BaseStar.StarType;
import au.com.codeka.common.protobuf.Messages;
import au.com.codeka.warworlds.R;
import au.com.codeka.warworlds.ServerGreeter;
import au.com.codeka.warworlds.StyledDialog;
import au.com.codeka.warworlds.ctrl.FleetListSimple;
import au.com.codeka.warworlds.ctrl.InfobarView;
import au.com.codeka.warworlds.ctrl.PlanetListSimple;
import au.com.codeka.warworlds.eventbus.EventHandler;
import au.com.codeka.warworlds.game.ScoutReportDialog;
import au.com.codeka.warworlds.game.SitrepActivity;
import au.com.codeka.warworlds.game.StarRenameDialog;
import au.com.codeka.warworlds.game.alliance.AllianceActivity;
import au.com.codeka.warworlds.game.empire.EmpireActivity;
import au.com.codeka.warworlds.game.solarsystem.SolarSystemActivity;
import au.com.codeka.warworlds.game.wormhole.WormholeFragment;
import au.com.codeka.warworlds.model.EmpireManager;
import au.com.codeka.warworlds.model.EmpireShieldManager;
import au.com.codeka.warworlds.model.Fleet;
import au.com.codeka.warworlds.model.MyEmpire;
import au.com.codeka.warworlds.model.Planet;
import au.com.codeka.warworlds.model.PurchaseManager;
import au.com.codeka.warworlds.model.Sector;
import au.com.codeka.warworlds.model.SectorManager;
import au.com.codeka.warworlds.model.ShieldManager;
import au.com.codeka.warworlds.model.Sprite;
import au.com.codeka.warworlds.model.SpriteDrawable;
import au.com.codeka.warworlds.model.Star;
import au.com.codeka.warworlds.model.StarImageManager;
import au.com.codeka.warworlds.model.StarManager;
import au.com.codeka.warworlds.model.StarSummary;
import au.com.codeka.warworlds.model.billing.IabException;
import au.com.codeka.warworlds.model.billing.IabHelper;
import au.com.codeka.warworlds.model.billing.IabResult;
import au.com.codeka.warworlds.model.billing.Purchase;
import au.com.codeka.warworlds.model.billing.SkuDetails;

import com.google.protobuf.InvalidProtocolBufferException;

/**
 * The \c StarfieldActivity is the "home" screen of the game, and displays the
 * starfield where you scroll around and interact with stars, etc.
 */
public class StarfieldActivity extends BaseStarfieldActivity {
    private static final Log log = new Log("StarfieldActivity");
    private Context mContext = this;
    private PlanetListSimple mPlanetList;
    private FleetListSimple mFleetList;
    private Star mSelectedStar;
    private Fleet mSelectedFleet;
    private StarSummary mHomeStar;
    private View mBottomPane;
    private Button mAllianceBtn;

    private Purchase mStarRenamePurchase;

    private Star mStarToSelect;
    private String mStarKeyToSelect;
    private Fleet mFleetToSelect;

    private boolean mDoNotNavigateToHomeStar;

    private static final int SOLAR_SYSTEM_REQUEST = 1;
    private static final int EMPIRE_REQUEST = 2;
    private static final int SITREP_REQUEST = 3;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPlanetList = (PlanetListSimple) findViewById(R.id.planet_list);
        mFleetList = (FleetListSimple) findViewById(R.id.fleet_list);

        findViewById(R.id.selected_star).setVisibility(View.GONE);
        findViewById(R.id.selected_fleet).setVisibility(View.GONE);
        mBottomPane = findViewById(R.id.bottom_pane);

        if (isPortrait()) {
            InfobarView infobar = (InfobarView) findViewById(R.id.infobar);
            infobar.hideEmpireName();
        }

        mPlanetList.setPlanetSelectedHandler(new PlanetListSimple.PlanetSelectedHandler() {
            @Override
            public void onPlanetSelected(Planet planet) {
                navigateToPlanet(mSelectedStar, planet, false);
            }
        });
        mFleetList.setFleetSelectedHandler(new FleetListSimple.FleetSelectedHandler() {
            @Override
            public void onFleetSelected(Fleet fleet) {
                if (mSelectedStar == null) {
                    return; //??
                }

                openEmpireActivityAtFleet(mSelectedStar, fleet);
            }
        });

        final Button empireBtn = (Button) findViewById(R.id.empire_btn);
        empireBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openEmpireActivity();
            }
        });

        final Button scoutReportBtn = (Button) findViewById(R.id.scout_report_btn);
        scoutReportBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSelectedStar != null) {
                    ScoutReportDialog dialog = new ScoutReportDialog();
                    dialog.setStar(mSelectedStar);
                    dialog.show(getSupportFragmentManager(), "");
                }
            }
        });

        final Button sitrepBtn = (Button) findViewById(R.id.sitrep_btn);
        sitrepBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSitrepActivity();
            }
        });

        final Button viewBtn = (Button) findViewById(R.id.view_btn);
        viewBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSelectedStar == null) {
                    return;
                }

                Intent intent = new Intent(mContext, SolarSystemActivity.class);
                intent.putExtra("au.com.codeka.warworlds.StarKey", mSelectedStar.getKey());
                startActivityForResult(intent, SOLAR_SYSTEM_REQUEST);
            }
        });

        final Button renameBtn = (Button) findViewById(R.id.rename_btn);
        renameBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onRenameClick();
            }
        });

        mAllianceBtn = (Button) findViewById(R.id.alliance_btn);
        mAllianceBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAllianceClick();
            }
        });

        if (savedInstanceState != null) {
            Star selectedStar = null;
            Fleet selectedFleet = null;

            try {
                byte[] star_bytes = savedInstanceState.getByteArray("au.com.codeka.warworlds.SelectedStar");
                if (star_bytes != null) {
                    Messages.Star star_pb = Messages.Star.parseFrom(star_bytes);
                    selectedStar = new Star();
                    selectedStar.fromProtocolBuffer(star_pb);
                }
            } catch (InvalidProtocolBufferException e) {
            }

            try {
                byte[] fleet_bytes = savedInstanceState.getByteArray("au.com.codeka.warworlds.SelectedFleet");
                if (fleet_bytes != null) {
                    Messages.Fleet fleet_pb = Messages.Fleet.parseFrom(fleet_bytes);
                    selectedFleet = new Fleet();
                    selectedFleet.fromProtocolBuffer(fleet_pb);
                }
            } catch (InvalidProtocolBufferException e) {
            }

            mStarToSelect = selectedStar;
            mFleetToSelect = selectedFleet;
        }
        if (savedInstanceState == null) {
            Intent intent = getIntent();
            if (intent != null && intent.getExtras() != null) {
                String starKey = intent.getExtras().getString("au.com.codeka.warworlds.StarKey");
                if (starKey != null) {
                    long sectorX = intent.getExtras().getLong("au.com.codeka.warworlds.SectorX");
                    long sectorY = intent.getExtras().getLong("au.com.codeka.warworlds.SectorY");
                    int offsetX = intent.getExtras().getInt("au.com.codeka.warworlds.OffsetX");
                    int offsetY = intent.getExtras().getInt("au.com.codeka.warworlds.OffsetY");
                    mStarfield.scrollTo(sectorX, sectorY, offsetX, Sector.SECTOR_SIZE - offsetY);
                    mDoNotNavigateToHomeStar = true;
                }
            }
        }

        hideBottomPane(true);
    }

    @Override
    public void onResume() {
        super.onResume();

        ServerGreeter.waitForHello(this, new ServerGreeter.HelloCompleteHandler() {
            @Override
            public void onHelloComplete(boolean success, ServerGreeter.ServerGreeting greeting) {
                if (!success) {
                    return;
                }

                if (mStarToSelect != null && mStarfield.getScene() != null) {
                    mSelectedStar = mStarToSelect;
                    mStarfield.getScene().selectStar(mStarToSelect.getKey());
                    mStarfield.scrollTo(mStarToSelect);
                    mStarToSelect = null;
                }

                if (mFleetToSelect != null && mStarfield.getScene() != null) {
                    mStarfield.getScene().selectFleet(mFleetToSelect.getKey());
                    mFleetToSelect = null;
                }

                MyEmpire myEmpire = EmpireManager.i.getEmpire();
                if (myEmpire == null) {
                    return;
                }

                BaseStar homeStar = myEmpire.getHomeStar();
                if (homeStar != null && (mHomeStar == null || !mHomeStar.getKey().equals(homeStar.getKey()))) {
                    mHomeStar = (StarSummary) homeStar;
                    if (!mDoNotNavigateToHomeStar) {
                        mStarfield.scrollTo(homeStar);
                    }
                }

                mDoNotNavigateToHomeStar = true;
            }
        });
    }

    @Override
    protected int getLayoutID() {
        return R.layout.starfield;
    }

    @Override
    public void onStart() {
        super.onStart();
        StarManager.eventBus.register(mEventHandler);
        ShieldManager.eventBus.register(mEventHandler);
        StarfieldSceneManager.eventBus.register(mEventHandler);
    }

    @Override
    public void onPostResume() {
        super.onPostResume();

        if (mStarRenamePurchase != null) {
            showStarRenamePopup(mStarRenamePurchase);
            mStarRenamePurchase = null;
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        StarfieldSceneManager.eventBus.unregister(mEventHandler);
        StarManager.eventBus.unregister(mEventHandler);
        ShieldManager.eventBus.unregister(mEventHandler);
    }

    @Override
    public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (mSelectedStar != null) {
            Messages.Star.Builder star_pb = Messages.Star.newBuilder();
            mSelectedStar.toProtocolBuffer(star_pb);
            state.putByteArray("au.com.codeka.warworlds.SelectedStar", star_pb.build().toByteArray());
        }

        if (mSelectedFleet != null) {
            Messages.Fleet.Builder fleet_pb = Messages.Fleet.newBuilder();
            mSelectedFleet.toProtocolBuffer(fleet_pb);
            state.putByteArray("au.com.codeka.warworlds.SelectedFleet", fleet_pb.build().toByteArray());
        }
    }

    public void openEmpireActivityAtFleet(Star star, Fleet fleet) {
        Intent intent = new Intent(mContext, EmpireActivity.class);
        intent.putExtra("au.com.codeka.warworlds.StarID", star.getID());
        intent.putExtra("au.com.codeka.warworlds.FleetID", Integer.parseInt(fleet.getKey()));
        startActivityForResult(intent, EMPIRE_REQUEST);
    }

    public void openEmpireActivity() {
        Intent intent = new Intent(mContext, EmpireActivity.class);
        startActivityForResult(intent, EMPIRE_REQUEST);
    }

    public void openSitrepActivity() {
        Intent intent = new Intent(mContext, SitrepActivity.class);
        startActivityForResult(intent, SITREP_REQUEST);
    }

    public void onAllianceClick() {
        Intent intent = new Intent(mContext, AllianceActivity.class);
        startActivity(intent);
    }

    public int getBottomPaneHeight() {
        return mBottomPane.getHeight();
    }

    private void hideBottomPane(boolean instant) {
        applyBottomPaneAnimation(false, instant);
    }

    private void showBottomPane() {
        applyBottomPaneAnimation(true, false);
    }

    private void applyBottomPaneAnimation(boolean isOpen, boolean instant) {
        float dp;
        if (isPortrait()) {
            if (isOpen) {
                dp = 180;
            } else {
                dp = 34;
            }
        } else {
            if (isOpen) {
                dp = 200;
            } else {
                dp = 100;
            }
        }

        Resources r = getResources();
        float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, r.getDisplayMetrics());

        if (isPortrait()) {
            if (instant) {
                RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) mBottomPane.getLayoutParams();
                lp.height = (int) px;
                mBottomPane.setLayoutParams(lp);
            } else {
                applyBottomPaneAnimationPortrait(px);
            }
        } else {
            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) mAllianceBtn.getLayoutParams();
            if (isOpen) {
                // NB: removeRule is not available until API level 17 :/
                lp.addRule(RelativeLayout.BELOW, 0);
                lp.addRule(RelativeLayout.ALIGN_PARENT_TOP, 1);
                lp.topMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 34, r.getDisplayMetrics());
            } else {
                lp.addRule(RelativeLayout.BELOW, R.id.empire_btn);
                lp.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0);
                lp.topMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, r.getDisplayMetrics());
            }
            mAllianceBtn.setLayoutParams(lp);

            if (instant) {
                lp = (RelativeLayout.LayoutParams) mBottomPane.getLayoutParams();
                lp.width = (int) px;
                mBottomPane.setLayoutParams(lp);
            } else {
                applyBottomPaneAnimationLandscape(px);
            }
        }
    }

    private void applyBottomPaneAnimationLandscape(final float pxWidth) {
        Animation a = new Animation() {
            private int mInitialWidth;

            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                final int newWidth = mInitialWidth + (int)((pxWidth - mInitialWidth) * interpolatedTime);
                mBottomPane.getLayoutParams().width = newWidth;
                mBottomPane.requestLayout();
            }

            @Override
            public void initialize(int width, int height, int parentWidth, int parentHeight) {
                super.initialize(width, height, parentWidth, parentHeight);
                mInitialWidth = width;
            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };
        a.setDuration(500);
        mBottomPane.setAnimation(a);

    }

    private void applyBottomPaneAnimationPortrait(final float pxHeight) {
        Animation a = new Animation() {
            private int mInitialHeight;

            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                final int newHeight = mInitialHeight + (int)((pxHeight - mInitialHeight) * interpolatedTime);
                mBottomPane.getLayoutParams().height = newHeight;
                mBottomPane.requestLayout();
            }

            @Override
            public void initialize(int width, int height, int parentWidth, int parentHeight) {
                super.initialize(width, height, parentWidth, parentHeight);
                mInitialHeight = height;
            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };
        a.setDuration(500);
        mBottomPane.setAnimation(a);
    }

    /**
     * Navigates to the given planet in the given star. Starts the SolarSystemActivity.
     * 
     * @param star
     * @param planet
     * @param scrollView If \c true, we'll also scroll the current view so that given star
     *         is centered on the given star.
     */
    public void navigateToPlanet(Star star, Planet planet, boolean scrollView) {
        navigateToPlanet(star.getStarType(), star.getSectorX(), star.getSectorY(), star.getKey(),
                         star.getOffsetX(), star.getOffsetY(), planet.getIndex(),
                         scrollView);
    }

    private void navigateToPlanet(StarType starType, long sectorX, long sectorY, String starKey, int starOffsetX,
                                  int starOffsetY, int planetIndex, boolean scrollView) {
        if (scrollView) {
            int offsetX = starOffsetX;
            int offsetY = starOffsetY;
            mStarfield.scrollTo(sectorX, sectorY, offsetX, Sector.SECTOR_SIZE - offsetY);
        }

        Intent intent;
        if (starType.getType() == Star.Type.Wormhole) {
            intent = new Intent(mContext, WormholeFragment.class);
        } else {
            intent = new Intent(mContext, SolarSystemActivity.class);
        }        intent.putExtra("au.com.codeka.warworlds.StarKey", starKey);
        intent.putExtra("au.com.codeka.warworlds.PlanetIndex", planetIndex);
        startActivityForResult(intent, SOLAR_SYSTEM_REQUEST);
    }

    public void navigateToFleet(final String starKey, final String fleetKey) {
        StarSummary star = StarManager.i.getStarSummary(Integer.parseInt(starKey));
        if (star == null) {
            StarManager.eventBus.register(new Object() {
                @EventHandler
                public void onStarUpdated(StarSummary star) {
                    if (star.getKey().equals(starKey)) {
                        navigateToFleet(star, star.findFleet(fleetKey));
                        StarManager.eventBus.unregister(this);
                    }
                }
            });

        } else {
            BaseFleet fleet = star.findFleet(fleetKey);
            if (fleet != null) {
                navigateToFleet(star, star.findFleet(fleetKey));
            }
        }
    }

    public void navigateToFleet(StarSummary star, BaseFleet fleet) {
        int offsetX = star.getOffsetX();
        int offsetY = star.getOffsetY();

        mStarfield.scrollTo(star.getSectorX(), star.getSectorY(), offsetX,
                Sector.SECTOR_SIZE - offsetY);

        if (mStarfield.getScene() == null) {
            // TODO: we should probably remember the fleet then navigate when the scene is
            // ready.
            return;
        }
        if (fleet.getState() == Fleet.State.MOVING) {
            mStarfield.getScene().selectFleet(fleet.getKey());
        } else {
            mStarfield.getScene().selectStar(star.getKey());
        }
    }

    public void onRenameClick() {
        SkuDetails starRenameSku;
        try {
            starRenameSku = PurchaseManager.i.getInventory().getSkuDetails("star_rename");
        } catch (IabException e) {
            log.error("Couldn't get SKU details!", e);
            return;
        }

        new StyledDialog.Builder(this)
                .setMessage(String.format(Locale.ENGLISH,
                        "Renaming stars costs %s. If you wish to continue, you'll be directed "+
                        "to the Play Store where you can purchase a one-time code to rename this "+
                        "star. Are you sure you want to continue?",
                        starRenameSku.getPrice()))
                .setTitle("Rename Star")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Rename", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        doRenameStar();
                        dialog.dismiss();
                    }
                })
                .create().show();
    }

    public void doRenameStar() {
        if (mSelectedStar == null) {
            return;
        }

        try {
            PurchaseManager.i.launchPurchaseFlow(this, "star_rename", new IabHelper.OnIabPurchaseFinishedListener() {
                @Override
                public void onIabPurchaseFinished(IabResult result, final Purchase info) {
                    if (mSelectedStar == null) {
                        return;
                    }

                    Purchase purchase = info;
                    boolean isSuccess = result.isSuccess();
                    if (result.isFailure() && result.getResponse() == IabHelper.BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED) {
                        // if they've already purchased a star-renamed, but not reclaimed it, then
                        // we let them through anyway.
                        log.debug("Already purchased a star-rename, we'll just show the popup.");
                        isSuccess = true;
                        try {
                            purchase = PurchaseManager.i.getInventory().getPurchase("star_rename");
                        } catch (IabException e) {
                            log.warning("Got an exception getting the purchase details.", e);
                        }
                    }

                    if (isSuccess) {
                        try {
                            showStarRenamePopup(purchase);
                        } catch(IllegalStateException e) {
                            // this can be called before the activity is resumed, so we just set a
                            // flag that'll cause us to pop up the dialog when the activity is resumed.
                            log.warning("Got an error trying to show the popup, we'll try again in a second...");
                            mStarRenamePurchase = purchase;
                        }
                    }
                }
            });
        } catch (IabException e) {
            log.error("Couldn't get SKU details!", e);
            return;
        }
    }

    private void showStarRenamePopup(Purchase purchase) {
        StarRenameDialog dialog = new StarRenameDialog();
        dialog.setPurchaseInfo(purchase);
        dialog.setStar(mSelectedStar);
        dialog.show(getSupportFragmentManager(), "");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == SOLAR_SYSTEM_REQUEST && intent != null) {
            boolean wasSectorUpdated = intent.getBooleanExtra(
                    "au.com.codeka.warworlds.SectorUpdated", false);
            long sectorX = intent.getLongExtra("au.com.codeka.warworlds.SectorX", 0);
            long sectorY = intent.getLongExtra("au.com.codeka.warworlds.SectorY", 0);
            String starKey = intent.getStringExtra("au.com.codeka.warworlds.StarKey");

            if (wasSectorUpdated) {
                SectorManager.i.refreshSector(sectorX, sectorY);
            } else if (starKey != null && mStarfield.getScene() != null) {
                // make sure we re-select the star you had selected before.
                mStarfield.getScene().selectStar(starKey);
            } else if (starKey != null) {
                mStarKeyToSelect = starKey;
            }
        } else if (requestCode == EMPIRE_REQUEST && intent != null) {
            EmpireActivity.EmpireActivityResult res = EmpireActivity.EmpireActivityResult.fromValue(
                    intent.getIntExtra("au.com.codeka.warworlds.Result", 0));

            final long sectorX = intent.getLongExtra("au.com.codeka.warworlds.SectorX", 0);
            final long sectorY = intent.getLongExtra("au.com.codeka.warworlds.SectorY", 0);
            final int starOffsetX = intent.getIntExtra("au.com.codeka.warworlds.StarOffsetX", 0);
            final int starOffsetY = intent.getIntExtra("au.com.codeka.warworlds.StarOffsetY", 0);
            final String starKey = intent.getStringExtra("au.com.codeka.warworlds.StarKey");

            if (res == EmpireActivity.EmpireActivityResult.NavigateToPlanet) {
                final int planetIndex = intent.getIntExtra("au.com.codeka.warworlds.PlanetIndex", 0);

                StarSummary star = StarManager.i.getStarSummary(Integer.parseInt(starKey));
                if (star == null) {
                    StarManager.eventBus.register(new Object() {
                        @EventHandler
                        public void onStarUpdated(StarSummary star) {
                            if (star.getKey().equals(starKey)) {
                                navigateToPlanet(star.getStarType(), sectorX, sectorY,
                                        starKey, starOffsetX, starOffsetY, planetIndex, true);
                                StarManager.eventBus.unregister(this);
                            }
                        }
                    });
                } else {
                    navigateToPlanet(star.getStarType(), sectorX, sectorY, starKey,
                            starOffsetX, starOffsetY, planetIndex, true);
                }
            } else if (res == EmpireActivity.EmpireActivityResult.NavigateToFleet) {
                String fleetKey = intent.getStringExtra("au.com.codeka.warworlds.FleetKey");

                navigateToFleet(starKey, fleetKey);
            }
        }
    }

    private void handleDeselect() {
        mSelectedStar = null;
        mSelectedFleet = null;

        findViewById(R.id.loading_container).setVisibility(View.GONE);
        findViewById(R.id.selected_star).setVisibility(View.GONE);
        findViewById(R.id.selected_fleet).setVisibility(View.GONE);

        hideBottomPane(false);
    }

    public Object mEventHandler = new Object() {
        @EventHandler
        public void onStarFetched(Star star) {
            if (mSelectedStar != null && mSelectedStar.getID() == star.getID()) {
                mSelectedStar = star;
                updateStarSelection();
            }
        }

        @EventHandler
        public void onShieldUpdated(ShieldManager.ShieldUpdatedEvent event) {
            EmpireShieldManager.i.clearTextureCache();

            if (mSelectedFleet != null) {
                StarfieldActivity.this.getEngine().runOnUpdateThread(new Runnable() {
                    @Override
                    public void run() {
                        // this will cause the selected fleet info to redraw and hence the shield
                        StarfieldActivity.this.onFleetSelected(mSelectedFleet);
                    }
                });
            }
        }

        @EventHandler
        public void onStarSelected(final StarfieldSceneManager.StarSelectedEvent event) {
            StarfieldActivity.this.onStarSelected(event.star);
        }

        @EventHandler
        public void onFleetSelected(final StarfieldSceneManager.FleetSelectedEvent event) {
            StarfieldActivity.this.onFleetSelected(event.fleet);
        }

        @EventHandler
        public void onSceneUpdated(final StarfieldSceneManager.SceneUpdatedEvent event) {
            if (mStarKeyToSelect != null) {
                event.scene.selectStar(mStarKeyToSelect);
            }
            if (mStarToSelect != null) {
                event.scene.selectStar(mStarToSelect.getKey());
            }
            if (mFleetToSelect != null) {
                event.scene.selectFleet(mFleetToSelect.getKey());
            }
            mStarToSelect = null;
            mFleetToSelect = null;
            mStarKeyToSelect = null;
        }
    };

    private void onStarSelected(Star star) {
        if (star == null) {
            handleDeselect();
            return;
        }

        if (mSelectedStar != null && mSelectedStar.getKey().equals(star.getKey())) {
            updateStarSelection();
            return;
        }
        mSelectedStar = star;
        mSelectedFleet = null;

        findViewById(R.id.loading_container).setVisibility(View.VISIBLE);
        findViewById(R.id.selected_star).setVisibility(View.GONE);
        findViewById(R.id.selected_fleet).setVisibility(View.GONE);
        showBottomPane();

        // force the star to refresh itself
        StarManager.i.refreshStar(star.getID());
    }

    private void updateStarSelection() {
        final View selectionLoadingContainer = findViewById(R.id.loading_container);
        final View selectedStarContainer = findViewById(R.id.selected_star);
        final View selectedFleetContainer = findViewById(R.id.selected_fleet);
        final TextView starName = (TextView) findViewById(R.id.star_name);
        final TextView starKind = (TextView) findViewById(R.id.star_kind);
        final ImageView starIcon = (ImageView) findViewById(R.id.star_icon);
        final Button renameButton = (Button) findViewById(R.id.rename_btn);

        selectionLoadingContainer.setVisibility(View.GONE);
        selectedStarContainer.setVisibility(View.VISIBLE);
        selectedFleetContainer.setVisibility(View.GONE);

        mPlanetList.setStar(mSelectedStar);
        mFleetList.setStar(mSelectedStar);

        MyEmpire myEmpire = EmpireManager.i.getEmpire();
        int numMyEmpire = 0;
        int numOtherEmpire = 0;
        for (BaseColony colony : mSelectedStar.getColonies()) {
            if (colony.getEmpireKey() == null) {
                continue;
            }
            if (colony.getEmpireKey().equals(myEmpire.getKey())) {
                numMyEmpire ++;
            } else {
                numOtherEmpire ++;
            }
        }
        if (numMyEmpire > numOtherEmpire) {
            renameButton.setVisibility(View.VISIBLE);
        } else {
            renameButton.setVisibility(View.GONE);
        }

        starName.setText(mSelectedStar.getName());
        starKind.setText(String.format(Locale.ENGLISH, "%s %s",
                mSelectedStar.getStarType().getShortName(),
                mSelectedStar.getCoordinateString()));
        Sprite starImage = StarImageManager.getInstance().getSprite(mSelectedStar, 80, true);
        starIcon.setImageDrawable(new SpriteDrawable(starImage));
    }

    private void onFleetSelected(final Fleet fleet) {
        if (fleet == null) {
            handleDeselect();
            return;
        }

        mSelectedStar = null;
        mSelectedFleet = fleet;

        final View selectionLoadingContainer = findViewById(R.id.loading_container);
        final View selectedStarContainer = findViewById(R.id.selected_star);
        final FleetInfoView fleetInfoView = (FleetInfoView) findViewById(R.id.selected_fleet);

        fleetInfoView.setFleet(fleet);
        selectionLoadingContainer.setVisibility(View.GONE);
        selectedStarContainer.setVisibility(View.GONE);
        fleetInfoView.setVisibility(View.VISIBLE);

        showBottomPane();
    }
}
