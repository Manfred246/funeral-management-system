package ru.funeralagency.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import ru.funeralagency.api.ApiClient;
import ru.funeralagency.api.FuneralRequestApi;
import ru.funeralagency.dto.CeremonyType;
import ru.funeralagency.dto.FuneralRequestDTO;
import ru.funeralagency.dto.RequestStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class MainController {
    @FXML
    private TableView<FuneralRequestDTO> requestsTable;

    @FXML
    private TableColumn<FuneralRequestDTO, Long> idColumn;

    @FXML
    private TableColumn<FuneralRequestDTO, Long> clientIdColumn;

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

    public MainController(){
        ApiClient apiClient = new ApiClient();
        this.requestApi = new FuneralRequestApi(apiClient);
    }

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory( new PropertyValueFactory<>("id") );

        clientIdColumn.setCellValueFactory( new PropertyValueFactory<>("clientId") );

        deceasedNameColumn.setCellValueFactory( new PropertyValueFactory<>("deceasedFullName") );

        ceremonyDateColumn.setCellValueFactory( new PropertyValueFactory<>("ceremonyDate") );

        ceremonyTypeTableColumn.setCellValueFactory( new PropertyValueFactory<>("ceremonyType") );

        statusColumn.setCellValueFactory( new PropertyValueFactory<>("status") );

        priceColumn.setCellValueFactory( new PropertyValueFactory<>("price") );

        loadRequests();
    }

    @FXML
    private void handleRefresh() {
        loadRequests();
    }

    private void loadRequests() {
        try {
            List<FuneralRequestDTO> requests = requestApi.findAll();

            requestsTable.setItems( FXCollections.observableArrayList(requests) );
        } catch (Exception e) {
            showError("Не удалось загрузить заявки", e.getMessage());
        }
    }

    private void showError(String title, String message){
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setHeaderText(title);
        alert.setContentText(message);

        alert.showAndWait();
    }
}
