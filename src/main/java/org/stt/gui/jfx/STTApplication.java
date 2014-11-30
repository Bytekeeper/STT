package org.stt.gui.jfx;

import com.google.common.base.Optional;
import com.sun.javafx.application.PlatformImpl;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.*;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.shape.Path;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.stt.CommandHandler;
import org.stt.config.TimeTrackingItemListConfig;
import org.stt.fun.Achievement;
import org.stt.fun.Achievements;
import org.stt.gui.jfx.TimeTrackingItemCell.ContinueActionHandler;
import org.stt.gui.jfx.TimeTrackingItemCell.DeleteActionHandler;
import org.stt.gui.jfx.TimeTrackingItemCell.EditActionHandler;
import org.stt.gui.jfx.binding.FirstItemOfDaySet;
import org.stt.gui.jfx.binding.TimeTrackingListFilter;
import org.stt.model.TimeTrackingItem;
import org.stt.model.TimeTrackingItemFilter;
import org.stt.persistence.IOUtil;
import org.stt.persistence.ItemReader;
import org.stt.persistence.ItemReaderProvider;
import org.stt.searching.ExpansionProvider;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;

public class STTApplication implements DeleteActionHandler, EditActionHandler,
        ContinueActionHandler {

    private static final Logger LOG = Logger.getLogger(STTApplication.class
            .getName());
    final ObservableList<TimeTrackingItem> allItems = FXCollections
            .observableArrayList();
    final StringProperty currentCommand = new SimpleStringProperty("");
    final IntegerProperty commandCaretPosition = new SimpleIntegerProperty();
    private final ExecutorService executorService;
    private final CommandHandler commandHandler;
    private final ItemReaderProvider historySourceProvider;
    private final ReportWindowBuilder reportWindowBuilder;
    private final ExpansionProvider expansionProvider;
    private final ResourceBundle localization;
    private final Achievements achievements;
    ObservableList<TimeTrackingItem> filteredList;
    // Property<TimeTrackingItem> selectedItem = new SimpleObjectProperty<>();
    ViewAdapter viewAdapter;

    private STTApplication(Builder builder) {
        this.expansionProvider = checkNotNull(builder.expansionProvider);
        this.reportWindowBuilder = checkNotNull(builder.reportWindowBuilder);
        this.commandHandler = checkNotNull(builder.commandHandler);
        this.historySourceProvider = checkNotNull(builder.historySourceProvider);
        this.executorService = checkNotNull(builder.executorService);
        this.localization = checkNotNull(builder.resourceBundle);
        this.achievements = checkNotNull(builder.achievements);
        TimeTrackingItemListConfig timeTrackingItemListConfig = checkNotNull(builder.timeTrackingItemListConfig);

        filteredList = new TimeTrackingListFilter(allItems, currentCommand,
                timeTrackingItemListConfig.isFilterDuplicatesWhenSearching());

    }

    protected void resultItemSelected(TimeTrackingItem item) {
        if (item != null && item.getComment().isPresent()) {
            String textToSet = item.getComment().get();
            textOfSelectedItem(textToSet);
        }
    }

    void readHistoryFrom(final ItemReader reader) {
        executorService.execute(new FutureTask<Void>(new Runnable() {

            @Override
            public void run() {
                Collection<TimeTrackingItem> allItems;
                try {
                    allItems = IOUtil.readAll(reader);
                } catch (IOException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                    throw new RuntimeException(ex);
                }
                viewAdapter.updateAllItems(allItems);
            }
        }, null));
    }

    public void textOfSelectedItem(String textToSet) {
        setCommandText(textToSet);
        viewAdapter.requestFocusOnCommandText();
    }

    private void setCommandText(String textToSet) {
        currentCommand.set(textToSet);
        commandCaretPosition.set(currentCommand.get().length());
    }

    void expandCurrentCommand() {
        List<String> expansions = getPossibleExpansions();
        if (!expansions.isEmpty()) {
            String maxExpansion = expansions.get(0);
            for (String exp : expansions) {
                maxExpansion = commonPrefix(maxExpansion, exp);
            }
            expandAtCaret(maxExpansion);
        }
    }

    private void expandAtCaret(String textToInsert) {
        int caretPosition = commandCaretPosition.get();
        String tail = currentCommand.get().substring(caretPosition);
        String expandedText = currentCommand.get().substring(0, caretPosition) + textToInsert;
        currentCommand.set(expandedText + tail);
        commandCaretPosition.set(expandedText.length());
    }

    private List<String> getPossibleExpansions() {
        int pos = commandCaretPosition.get();
        String textToExpand = currentCommand.get().substring(0, pos);
        return expansionProvider
                .getPossibleExpansions(textToExpand);
    }

    String commonPrefix(String a, String b) {
        for (int i = 0; i < a.length() && i < b.length(); i++) {
            if (a.charAt(i) != b.charAt(i)) {
                return a.substring(0, i);
            }
        }
        return a;
    }

    protected Optional<TimeTrackingItem> executeCommand() {
        final String text = currentCommand.get();
        if (!text.trim().isEmpty()) {
            Optional<TimeTrackingItem> result = commandHandler
                    .executeCommand(text);
            clearCommand();
            return result;
        }
        return Optional.<TimeTrackingItem>absent();
    }

    private void clearCommand() {
        currentCommand.set("");
    }

    public void show(Stage stage) {
        viewAdapter = new ViewAdapter(stage);
        viewAdapter.show();
    }

    public void start(final Stage stage) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                try {
                    show(stage);
                    readHistoryFrom(historySourceProvider.provideReader());
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "Couldn't start", e);
                }
            }
        });
    }

    @Override
    public void continueItem(TimeTrackingItem item) {
        commandHandler.resumeGivenItem(item);
        viewAdapter.shutdown();
    }

    @Override
    public void edit(TimeTrackingItem item) {
        setCommandText(commandHandler.itemToCommand(item));
    }

    @Override
    public void delete(TimeTrackingItem item) {
        try {
            commandHandler.delete(checkNotNull(item));
            allItems.remove(item);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static class Builder {

        private ExecutorService executorService;
        private CommandHandler commandHandler;
        private ItemReaderProvider historySourceProvider;
        private ReportWindowBuilder reportWindowBuilder;
        private ExpansionProvider expansionProvider;
        private ResourceBundle resourceBundle;
        private Achievements achievements;
        private TimeTrackingItemListConfig timeTrackingItemListConfig;

        public Builder executorService(ExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        public Builder commandHandler(CommandHandler commandHandler) {
            this.commandHandler = commandHandler;
            return this;
        }

        public Builder historySourceProvider(
                ItemReaderProvider historySourceProvider) {
            this.historySourceProvider = historySourceProvider;
            return this;
        }

        public Builder reportWindowBuilder(
                ReportWindowBuilder reportWindowBuilder) {
            this.reportWindowBuilder = reportWindowBuilder;
            return this;
        }

        public Builder expansionProvider(ExpansionProvider expansionProvider) {
            this.expansionProvider = expansionProvider;
            return this;
        }

        public Builder resourceBundle(ResourceBundle resourceBundle) {
            this.resourceBundle = resourceBundle;
            return this;
        }

        public Builder achievements(Achievements achievements) {
            this.achievements = achievements;
            return this;
        }

        public Builder timeTrackingItemListConfig(
                TimeTrackingItemListConfig timeTrackingItemListConfig) {
            this.timeTrackingItemListConfig = timeTrackingItemListConfig;
            return this;
        }

        public STTApplication build() {
            return new STTApplication(this);
        }

    }

    public class ViewAdapter {

        private final Stage stage;

        @FXML
        TextArea commandText;

        @FXML
        Button finButton;

        @FXML
        Button insertButton;

        @FXML
        ListView<TimeTrackingItem> result;

        @FXML
        FlowPane achievements;
        private Path cursorPath;
        private ObjectBinding<Bounds> caretPositionBinding;
        private Popup autocompletionPopup;
        private ListView<String> autoCompleteList = new ListView<>();

        ViewAdapter(Stage stage) {
            this.stage = stage;
        }

        protected void show() throws RuntimeException {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/org/stt/gui/jfx/MainWindow.fxml"), localization);
            loader.setController(this);

            BorderPane pane;
            try {
                pane = (BorderPane) loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            for (Achievement achievement : STTApplication.this.achievements
                    .getReachedAchievements()) {
                final String imageName = "/achievements/"
                        + achievement.getCode() + ".png";
                InputStream imageStream = getClass().getResourceAsStream(
                        imageName);
                if (imageStream != null) {
                    final ImageView imageView = new ImageView(new Image(
                            imageStream));
                    String description = achievement.getDescription();
                    if (description != null) {
                        Tooltip.install(imageView, new Tooltip(description));
                    }
                    achievements.getChildren().add(imageView);
                } else {
                    LOG.severe("Image " + imageName + " not found!");
                }
            }

            Scene scene = new Scene(pane);

            stage.setScene(scene);
            stage.setTitle(localization.getString("window.title"));
            Image applicationIcon = new Image("/Logo.png", 32, 32, true, true);
            stage.getIcons().add(applicationIcon);

            stage.setOnCloseRequest(new EventHandler<WindowEvent>() {
                @Override
                public void handle(WindowEvent arg0) {
                    Platform.runLater(new Runnable() {

                        @Override
                        public void run() {
                            shutdown();
                        }
                    });
                }
            });
            scene.setOnKeyPressed(new EventHandler<KeyEvent>() {

                @Override
                public void handle(KeyEvent event) {
                    if (KeyCode.ESCAPE.equals(event.getCode())) {
                        event.consume();
                        shutdown();
                    }
                }
            });

            stage.show();
            requestFocusOnCommandText();
            Skin<?> skin = commandText.getSkin();
            Set<Node> nodes = commandText.lookupAll("*");
            for (Node node : nodes) {
                if (node instanceof Path) {
                    cursorPath = (Path) node;
                }
            }
            caretPositionBinding = new ObjectBinding<Bounds>() {
                {
                    bind(cursorPath.layoutBoundsProperty(), stage.xProperty(), stage.yProperty());
                }

                @Override
                protected Bounds computeValue() {
                    return cursorPath.localToScreen(cursorPath.getLayoutBounds());
                }
            };

            autocompletionPopup = new Popup();
            autocompletionPopup.getContent().add(autoCompleteList);
            autoCompleteList.setPrefHeight(100);
            autoCompleteList.setOnKeyPressed(new EventHandler<KeyEvent>() {
                @Override
                public void handle(KeyEvent event) {
                    switch (event.getCode()) {
                        case ENTER:
                            if (!autoCompleteList.getSelectionModel().isEmpty()) {
                                String selectedItem = autoCompleteList.getSelectionModel().getSelectedItem();
                                expandAtCaret(selectedItem);
                            }
                            event.consume();
                            break;
                        case ESCAPE:
                            autocompletionPopup.hide();
                            event.consume();
                            break;
                    }
                }
            });

            caretPositionBinding.addListener(new ChangeListener<Bounds>() {
                @Override
                public void changed(ObservableValue<? extends Bounds> observable, Bounds oldValue, Bounds newValue) {
                    updateAutocompletionPopup();
                }
            });
            updateAutocompletionPopup();
        }

        private void updateAutocompletionPopup() {
            List<String> possibleExpansions = getPossibleExpansions();
            if (possibleExpansions.isEmpty()) {
                autocompletionPopup.hide();
            } else {
                Bounds caretBounds = caretPositionBinding.get();
                autocompletionPopup.setX(caretBounds.getMaxX());
                autocompletionPopup.setY(caretBounds.getMaxY());

                autoCompleteList.setItems(FXCollections.observableList(possibleExpansions));
                autoCompleteList.getSelectionModel().select(0);
                if (!autocompletionPopup.isShowing()) {
                    autocompletionPopup.show(stage);
                }
            }
        }

        protected void shutdown() {
            stage.close();
            executorService.shutdown();
            Platform.exit();
            // Required because txExit() above is just swallowed...
            PlatformImpl.tkExit();
            try {
                executorService.awaitTermination(1, TimeUnit.SECONDS);
                commandHandler.close();
            } catch (InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }
            // System.exit(0);
        }

        public void initialize() {
            insertButton.setMnemonicParsing(true);
            finButton.setMnemonicParsing(true);
            setupCellFactory();
            final MultipleSelectionModel<TimeTrackingItem> selectionModel = result
                    .getSelectionModel();
            selectionModel.setSelectionMode(SelectionMode.SINGLE);

            bindCaretPosition();
            commandText.textProperty().bindBidirectional(currentCommand);
            result.setItems(filteredList);
            bindItemSelection();
        }

        private void bindItemSelection() {
            result.setOnMouseClicked(new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent event) {
                    TimeTrackingItem selectedItem = result.getSelectionModel()
                            .getSelectedItem();
                    resultItemSelected(selectedItem);
                }
            });
        }

        private void setupCellFactory() {
            result.setCellFactory(new TimeTrackingItemCellFactory(
                    STTApplication.this, STTApplication.this,
                    STTApplication.this, new TimeTrackingItemFilter() {
                ObservableSet<TimeTrackingItem> firstItemOfDayBinding = new FirstItemOfDaySet(
                        allItems);

                @Override
                public boolean filter(TimeTrackingItem item) {
                    return firstItemOfDayBinding.contains(item);
                }
            }, localization));
        }

        private void bindCaretPosition() {
            commandCaretPosition.addListener(new InvalidationListener() {
                @Override
                public void invalidated(Observable observable) {
                    commandText.positionCaret(commandCaretPosition.get());
                }
            });
            commandText.caretPositionProperty().addListener(
                    new InvalidationListener() {
                        @Override
                        public void invalidated(Observable observable) {
                            commandCaretPosition.set(commandText
                                    .getCaretPosition());
                        }
                    });
        }

        protected void requestFocusOnCommandText() {
            PlatformImpl.runLater(new Runnable() {
                @Override
                public void run() {
                    commandText.requestFocus();
                }
            });
        }

        protected void updateAllItems(
                final Collection<TimeTrackingItem> updateWith) {
            PlatformImpl.runLater(new Runnable() {
                @Override
                public void run() {
                    allItems.setAll(updateWith);
                    viewAdapter.requestFocusOnCommandText();
                }
            });
        }

        @FXML
        void showReportWindow() {
            try {
                reportWindowBuilder.setupStage();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @FXML
        private void done() {
            executeCommand();
            shutdown();
        }

        @FXML
        void insert() {
            Optional<TimeTrackingItem> item = executeCommand();
            if (item.isPresent()) {
                readHistoryFrom(historySourceProvider.provideReader());
            }
        }

        @FXML
        private void fin() {
            commandHandler.endCurrentItem();
            shutdown();
        }

        @FXML
        private void onKeyPressed(KeyEvent event) {
            if (KeyCode.ENTER.equals(event.getCode()) && event.isControlDown()) {
                event.consume();
                done();
            }
            if (KeyCode.SPACE.equals(event.getCode()) && event.isControlDown()) {
                expandCurrentCommand();
                event.consume();
            }
            if (KeyCode.F1.equals(event.getCode())) {
                try {
                    Desktop.getDesktop()
                            .browse(new URI(
                                    "https://github.com/Bytekeeper/STT/wiki/CLI"));
                } catch (IOException | URISyntaxException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
        }

    }
}
