package ru.funeralagency.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import ru.funeralagency.api.ApiClient;
import ru.funeralagency.api.ClientApi;
import ru.funeralagency.api.FuneralRequestApi;
import ru.funeralagency.dto.CeremonyType;
import ru.funeralagency.dto.ClientDTO;
import ru.funeralagency.dto.FuneralRequestDTO;
import ru.funeralagency.dto.RequestStatus;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainController {
    private static final String SEARCH_BY_NAME = "По ФИО умершего";
    private static final String SEARCH_BY_CLIENT = "По клиенту";
    private static final String ALL_FILTER_VALUES = "Все";

    @FXML
    private ComboBox<String> searchTypeComboBox;

    @FXML
    private TextField deceasedSearchField;

    @FXML
    private ComboBox<ClientDTO> clientSearchComboBox;

    @FXML
    private ComboBox<String> statusFilterComboBox;

    @FXML
    private ComboBox<String> ceremonyTypeFilterComboBox;

    @FXML
    private DatePicker dateFromFilterPicker;

    @FXML
    private DatePicker dateToFilterPicker;

    @FXML
    private TableView<FuneralRequestDTO> requestsTable;

    @FXML
    private TableColumn<FuneralRequestDTO, Long> idColumn;

    @FXML
    private TableColumn<FuneralRequestDTO, String> clientNameColumn;

    @FXML
    private TableColumn<FuneralRequestDTO, String> deceasedNameColumn;

    @FXML
    private TableColumn<FuneralRequestDTO, LocalDate> ceremonyDateColumn;

    @FXML
    private TableColumn<FuneralRequestDTO, CeremonyType> ceremonyTypeTableColumn;

    @FXML
    private TableColumn<FuneralRequestDTO, RequestStatus> statusColumn;

    @FXML
    private TableColumn<FuneralRequestDTO, BigDecimal> priceColumn;

    private final FuneralRequestApi requestApi;
    private final ClientApi clientApi;
    private final Map<Long, String> clientNames = new HashMap<>();
    private boolean resettingFilters;

    public MainController() {
        ApiClient apiClient = new ApiClient();
        this.requestApi = new FuneralRequestApi(apiClient);
        this.clientApi = new ClientApi(apiClient);
    }

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));

        clientNameColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                clientNames.getOrDefault(cell.getValue().getClientId(), "Клиент не найден")
        ));

        deceasedNameColumn.setCellValueFactory(new PropertyValueFactory<>("deceasedFullName"));

        ceremonyDateColumn.setCellValueFactory(new PropertyValueFactory<>("ceremonyDate"));

        ceremonyTypeTableColumn.setCellValueFactory(new PropertyValueFactory<>("ceremonyType"));

        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        priceColumn.setCellValueFactory(new PropertyValueFactory<>("price"));

        searchTypeComboBox.setItems(FXCollections.observableArrayList(SEARCH_BY_NAME, SEARCH_BY_CLIENT));
        searchTypeComboBox.getSelectionModel().selectFirst();

        initializeFilters();
        loadRequests();
    }

    private void initializeFilters() {
        statusFilterComboBox.getItems().add(ALL_FILTER_VALUES);
        for (RequestStatus status : RequestStatus.values()) {
            statusFilterComboBox.getItems().add(status.name());
        }
        statusFilterComboBox.getSelectionModel().selectFirst();

        ceremonyTypeFilterComboBox.getItems().add(ALL_FILTER_VALUES);
        for (CeremonyType type : CeremonyType.values()) {
            ceremonyTypeFilterComboBox.getItems().add(type.name());
        }
        ceremonyTypeFilterComboBox.getSelectionModel().selectFirst();

        ChangeListener<Object> filterListener = (observable, oldValue, newValue) -> handleApplyFilters();
        statusFilterComboBox.valueProperty().addListener(filterListener);
        ceremonyTypeFilterComboBox.valueProperty().addListener(filterListener);
        dateFromFilterPicker.valueProperty().addListener(filterListener);
        dateToFilterPicker.valueProperty().addListener(filterListener);
    }

    @FXML
    private void handleApplyFilters() {
        if (resettingFilters) {
            return;
        }

        LocalDate dateFrom = dateFromFilterPicker.getValue();
        LocalDate dateTo = dateToFilterPicker.getValue();
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            showWarning("Фильтрация заявок", "Дата «от» не должна быть позже даты «до».");
            return;
        }

        String statusValue = statusFilterComboBox.getValue();
        RequestStatus status = statusValue == null || ALL_FILTER_VALUES.equals(statusValue)
                ? null : RequestStatus.valueOf(statusValue);
        String typeValue = ceremonyTypeFilterComboBox.getValue();
        CeremonyType ceremonyType = typeValue == null || ALL_FILTER_VALUES.equals(typeValue)
                ? null : CeremonyType.valueOf(typeValue);

        try {
            List<FuneralRequestDTO> requests = requestApi.filter(
                    status,
                    ceremonyType,
                    dateFrom,
                    dateTo
            );
            requestsTable.setItems(FXCollections.observableArrayList(requests));
        } catch (IOException exception) {
            showError("Не удалось применить фильтры", exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            showError("Не удалось применить фильтры", "Фильтрация прервана.");
        }
    }

    @FXML
    private void handleResetFilters() {
        resettingFilters = true;
        try {
            statusFilterComboBox.getSelectionModel().selectFirst();
            ceremonyTypeFilterComboBox.getSelectionModel().selectFirst();
            dateFromFilterPicker.setValue(null);
            dateToFilterPicker.setValue(null);
        } finally {
            resettingFilters = false;
        }
        loadRequests();
    }

    @FXML
    private void handleSearchTypeChange() {
        boolean byName = SEARCH_BY_NAME.equals(searchTypeComboBox.getValue());
        deceasedSearchField.setVisible(byName);
        deceasedSearchField.setManaged(byName);
        clientSearchComboBox.setVisible(!byName);
        clientSearchComboBox.setManaged(!byName);
    }

    @FXML
    private void handleSearch() {
        try {
            List<FuneralRequestDTO> requests;
            if (SEARCH_BY_NAME.equals(searchTypeComboBox.getValue())) {
                String deceasedName = deceasedSearchField.getText().trim();
                if (deceasedName.isBlank()) {
                    showWarning("Поиск заявок", "Введите ФИО умершего или его часть.");
                    return;
                }
                requests = requestApi.searchByDeceasedName(deceasedName);
            } else if (SEARCH_BY_CLIENT.equals(searchTypeComboBox.getValue())) {
                ClientDTO client = clientSearchComboBox.getValue();
                if (client == null) {
                    showWarning("Поиск заявок", "Выберите клиента из списка.");
                    return;
                }
                requests = requestApi.searchByClient(client.getId());
            } else {
                showWarning("Поиск заявок", "Выберите тип поиска.");
                return;
            }
            requestsTable.setItems(FXCollections.observableArrayList(requests));
        } catch (IOException exception) {
            showError("Не удалось выполнить поиск", exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            showError("Не удалось выполнить поиск", "Поиск прерван.");
        }
    }

    @FXML
    private void handleResetSearch() {
        deceasedSearchField.clear();
        clientSearchComboBox.getSelectionModel().clearSelection();
        searchTypeComboBox.getSelectionModel().selectFirst();
        loadRequests();
    }

    @FXML
    private void handleRefresh() {
        loadRequests();
    }

    @FXML
    private void handleExport() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранить заявки в Excel");
        fileChooser.setInitialFileName("funeral-requests.xlsx");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel (*.xlsx)", "*.xlsx"));

        File file = fileChooser.showSaveDialog(requestsTable.getScene().getWindow());
        if (file == null) {
            return;
        }
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            showWarning("Экспорт в Excel", "Укажите имя файла с расширением .xlsx.");
            return;
        }

        try {
            byte[] content = requestApi.exportRequests();
            Files.write(file.toPath(), content);

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.initOwner(requestsTable.getScene().getWindow());
            alert.setTitle("Экспорт в Excel");
            alert.setHeaderText("Заявки успешно сохранены");
            alert.setContentText(file.getAbsolutePath());
            alert.showAndWait();
        } catch (IOException exception) {
            showError("Не удалось экспортировать заявки", exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            showError("Не удалось экспортировать заявки", "Экспорт прерван.");
        }
    }

    @FXML
    private void handleStatistics() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    MainController.class.getResource("/ru/funeralagency/view/statistics-view.fxml")
            );
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().addAll(requestsTable.getScene().getStylesheets());
            StatisticsController controller = loader.getController();

            Stage stage = new Stage();
            stage.setTitle("Статистика");
            stage.initOwner(requestsTable.getScene().getWindow());
            stage.initModality(Modality.WINDOW_MODAL);
            stage.setScene(scene);
            stage.setMinWidth(540);
            stage.setMinHeight(480);
            stage.show();
            controller.loadStatistics();
        } catch (IOException exception) {
            showError("Не удалось открыть статистику", exception.getMessage());
        }
    }

    @FXML
    private void handleAdd() {
        openRequestForm(null);
    }

    @FXML
    private void handleEdit() {
        FuneralRequestDTO selectedRequest = requestsTable.getSelectionModel().getSelectedItem();
        if (selectedRequest == null) {
            showWarning("Заявка не выбрана", "Выберите строку в таблице заявок и нажмите «Изменить».");
            return;
        }
        openRequestForm(selectedRequest);
    }

    @FXML
    private void handleDelete() {
        FuneralRequestDTO selectedRequest = requestsTable.getSelectionModel().getSelectedItem();
        if (selectedRequest == null) {
            showWarning("Заявка не выбрана", "Выберите строку в таблице заявок и нажмите «Удалить».");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.initOwner(requestsTable.getScene().getWindow());
        confirmation.setTitle("Удаление заявки");
        confirmation.setHeaderText("Удалить выбранную заявку?");
        confirmation.setContentText(
                "ID: " + selectedRequest.getId()
                        + "\nФИО умершего: " + selectedRequest.getDeceasedFullName()
        );
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        try {
            requestApi.delete(selectedRequest.getId());
            loadRequests();
        } catch (IOException exception) {
            showError("Не удалось удалить заявку", exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            showError("Не удалось удалить заявку", "Удаление прервано.");
        }
    }

    private void openRequestForm(FuneralRequestDTO selectedRequest) {
        try {
            List<ClientDTO> clients = clientApi.findAll();
            FXMLLoader loader = new FXMLLoader(
                    MainController.class.getResource("/ru/funeralagency/view/request-form.fxml")
            );
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().addAll(requestsTable.getScene().getStylesheets());
            RequestFormController controller = loader.getController();
            if (selectedRequest == null) {
                controller.initializeForm(requestApi, clients);
            } else {
                controller.initializeEditForm(requestApi, clients, selectedRequest);
            }

            Stage stage = new Stage();
            stage.setTitle(selectedRequest == null ? "Добавление заявки" : "Изменение заявки");
            stage.initOwner(requestsTable.getScene().getWindow());
            stage.initModality(Modality.WINDOW_MODAL);
            stage.setScene(scene);
            stage.setMinWidth(580);
            stage.setMinHeight(480);
            stage.showAndWait();

            if (controller.isSaved()) {
                loadRequests();
            }
        } catch (IOException exception) {
            showError("Не удалось открыть форму заявки", exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            showError("Не удалось загрузить клиентов", "Загрузка прервана.");
        }
    }

    private void loadRequests() {
        try {
            List<ClientDTO> clients = clientApi.findAll();
            List<FuneralRequestDTO> requests = requestApi.findAll();

            clientNames.clear();
            for (ClientDTO client : clients) {
                clientNames.put(client.getId(), client.getFullName());
            }

            clientSearchComboBox.setItems(FXCollections.observableArrayList(clients));
            requestsTable.setItems(FXCollections.observableArrayList(requests));
        } catch (IOException exception) {
            showError("Не удалось загрузить заявки и клиентов", exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            showError("Не удалось загрузить заявки и клиентов", "Загрузка прервана.");
        }
    }

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.initOwner(requestsTable.getScene().getWindow());
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.getDialogPane().getStylesheets().add(
                MainController.class.getResource("/ru/funeralagency/css/application.css").toExternalForm()
        );
        alert.setTitle("Ошибка");
        alert.setHeaderText(title);
        alert.setContentText(message);

        alert.showAndWait();
    }
}
