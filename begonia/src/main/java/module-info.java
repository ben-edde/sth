module org.lima.begonia {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires eu.hansolo.tilesfx;
    requires org.apache.commons.lang3;
    requires java.desktop;

    opens org.lima.begonia to javafx.fxml;
    exports org.lima.begonia;
}