function updateDateTime() {
    const dateElement = document.getElementById("date");
    const now = new Date();

    // Pobieramy dzień tygodnia, datę i godzinę
    const days = ["Niedziela", "Poniedziałek", "Wtorek", "Środa", "Czwartek", "Piątek", "Sobota"];
    const dayName = days[now.getDay()];

    const formattedDate = now.toLocaleDateString("pl-PL", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit"
    });

    const formattedTime = now.toLocaleTimeString("pl-PL", {
        hour: "2-digit",
        minute: "2-digit",
    });

    dateElement.textContent = `${dayName}, ${formattedDate} ${formattedTime}`;
}

// Uruchamiamy funkcję od razu i aktualizujemy co sekundę
document.addEventListener("DOMContentLoaded", () => {
    updateDateTime();
    setInterval(updateDateTime, 1000);
});
