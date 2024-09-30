module org.example.demo3 {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;

    // Öffnen des Hauptpakets für JavaFX FXML
    opens org.example.demo3 to javafx.fxml;

    // Exportieren des SplitVote-Pakets an javafx.graphics
    exports org.example.demo3.splitvote to javafx.graphics;

    // Exportieren des Hauptpakets
    exports org.example.demo3;
}
