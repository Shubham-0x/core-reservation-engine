const API = "/api";

const $ = (id) => document.getElementById(id);

const eventSelect = $("eventSelect");
const showSelect = $("showSelect");
const loadSeatsButton = $("loadSeats");
const seatArea = $("seatArea");
const seatGrid = $("seatGrid");
const bookButton = $("bookButton");
const selectionText = $("selectionText");
const eventInfo = $("eventInfo");
const bookingIdInput = $("bookingId");
const cancelButton = $("cancelButton");
const toast = $("toast");
const loginForm = $("loginForm");
const registerForm = $("registerForm");
const userStatus = $("userStatus");
const logoutButton = $("logoutButton");
const historyList = $("historyList");
const refreshHistoryButton = $("refreshHistory");

let currentSeats = [];
let selectedSeatId = null;
let toastTimer;
let authHeader = null;
let authenticatedEmail = null;

function showToast(message, isError = false) {
    toast.textContent = message;
    toast.classList.toggle("error", isError);
    toast.classList.add("show");

    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
        toast.classList.remove("show");
    }, 3500);
}

async function apiRequest(url, options = {}) {
    const headers = new Headers(options.headers || {});

    if (authHeader) {
        headers.set("Authorization", authHeader);
    }

    const response = await fetch(url, {
        ...options,
        headers
    });

    const text = await response.text();
    let data = null;

    if (text) {
        try {
            data = JSON.parse(text);
        } catch {
            data = text;
        }
    }

    if (!response.ok) {
        const message =
            typeof data === "object" && data !== null
                ? data.message || data.error || data.detail
                : data;

        const error = new Error(
            message || `Request failed with HTTP ${response.status}`
        );

        error.status = response.status;
        throw error;
    }

    return data;
}

// Credentials are kept only in memory, not in localStorage.
function setSignedIn(email, basicToken) {
    authHeader = `Basic ${basicToken}`;
    authenticatedEmail = email;
    userStatus.textContent = `Signed in as ${email}`;
    logoutButton.hidden = false;
}

function clearAuth() {
    authHeader = null;
    authenticatedEmail = null;
    userStatus.textContent = "Not signed in";
    logoutButton.hidden = true;
    historyList.innerHTML =
        '<p class="field-hint">Sign in to see your bookings here.</p>';
}

function ensureAuthenticated() {
    if (authHeader && authenticatedEmail) {
        return true;
    }

    showToast("Sign in first, using the account form above.", true);
    $("authSection").scrollIntoView({ behavior: "smooth" });
    $("loginEmail").focus({ preventScroll: true });
    return false;
}

async function signIn(email, password) {
    const basicToken = btoa(`${email}:${password}`);

    const response = await fetch(`${API}/auth/login`, {
        method: "POST",
        headers: { Authorization: `Basic ${basicToken}` }
    });

    const text = await response.text();
    let data = {};

    try {
        data = text ? JSON.parse(text) : {};
    } catch {
        data = { message: text };
    }

    if (!response.ok) {
        throw new Error(
            data.message || data.error || "Login failed. Check your credentials."
        );
    }

    setSignedIn(email, basicToken);
    showToast(`Signed in as ${email}`);
    await loadHistory();
}

async function handleLogin(event) {
    event.preventDefault();

    const email = $("loginEmail").value.trim();
    const password = $("loginPassword").value;

    if (!email || !password) {
        showToast("Enter your email and password.", true);
        return;
    }

    try {
        await signIn(email, password);
    } catch (error) {
        showToast(`Login failed: ${error.message}`, true);
    }
}

async function handleRegister(event) {
    event.preventDefault();

    const name = $("registerName").value.trim();
    const email = $("registerEmail").value.trim();
    const password = $("registerPassword").value;

    if (!name || !email || password.length < 8) {
        showToast("Enter a name, an email, and a password of at least 8 characters.", true);
        return;
    }

    try {
        await apiRequest(`${API}/auth/register`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ name, email, password })
        });

        showToast("Account created. Now sign in with your new details.");
        $("loginEmail").value = email;
        $("loginPassword").value = "";
        $("loginPassword").focus();
        registerForm.reset();
    } catch (error) {
        showToast(`Registration failed: ${error.message}`, true);
    }
}

function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (char) => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#039;"
    })[char]);
}

async function loadEvents() {
    eventSelect.innerHTML = '<option value="">Loading events…</option>';

    try {
        const events = await apiRequest(`${API}/events`);

        eventSelect.innerHTML =
            '<option value="">Select an event</option>';

        if (!Array.isArray(events) || events.length === 0) {
            eventSelect.innerHTML =
                '<option value="">No events available</option>';
            return;
        }

        events.forEach((event) => {
            const option = document.createElement("option");
            option.value = event.id;
            option.textContent = event.name;
            option.dataset.description = event.description || "";
            eventSelect.appendChild(option);
        });
    } catch (error) {
        eventSelect.innerHTML =
            '<option value="">Could not load events</option>';
        showToast(`Could not load events: ${error.message}`, true);
    }
}

async function loadShows(eventId) {
    showSelect.innerHTML = '<option value="">Loading shows…</option>';
    seatArea.hidden = true;
    resetSelection();

    if (!eventId) {
        showSelect.innerHTML =
            '<option value="">Select an event first</option>';
        return;
    }

    try {
        const shows = await apiRequest(
            `${API}/events/${encodeURIComponent(eventId)}/shows`
        );

        showSelect.innerHTML = '<option value="">Select a show</option>';

        if (!Array.isArray(shows) || shows.length === 0) {
            showSelect.innerHTML =
                '<option value="">No shows for this event</option>';
            return;
        }

        shows.forEach((show) => {
            const option = document.createElement("option");
            option.value = show.id;
            option.textContent = show.showTime
                ? new Date(show.showTime).toLocaleString()
                : `Show ${show.id}`;
            showSelect.appendChild(option);
        });
    } catch (error) {
        showSelect.innerHTML =
            '<option value="">Could not load shows</option>';
        showToast(`Could not load shows: ${error.message}`, true);
    }
}

function updateEventInfo() {
    const option = eventSelect.selectedOptions[0];

    if (!option || !option.value) {
        eventInfo.hidden = true;
        return;
    }

    const description =
        option.dataset.description || "No description available.";

    eventInfo.textContent = `${option.textContent} · ${description}`;
    eventInfo.hidden = false;
}

function resetSelection() {
    selectedSeatId = null;
    bookButton.disabled = true;
    selectionText.textContent = "No seat selected";
}

function renderSeats() {
    seatGrid.replaceChildren();
    resetSelection();

    currentSeats.forEach((seat) => {
        const button = document.createElement("button");
        const isAvailable = seat.status === "AVAILABLE";
        const label = seat.seatNumber || `S${seat.seatId ?? seat.id}`;

        button.type = "button";
        button.className =
            `seat ${isAvailable ? "available" : "booked"}`;

        button.textContent = label;

        button.title = isAvailable
            ? `Select seat ${label}`
            : `Seat ${label} is booked`;

        button.setAttribute("aria-label", button.title);
        button.disabled = !isAvailable;

        button.addEventListener("click", () => {
            selectedSeatId = seat.id;

            seatGrid.querySelectorAll(".seat").forEach((item) => {
                item.classList.remove("selected");
            });

            button.classList.add("selected");
            bookButton.disabled = false;
            selectionText.textContent =
                `Seat ${label} selected`;
        });

        seatGrid.appendChild(button);
    });
}

async function loadSeats() {
    const showId = showSelect.value;

    if (!showId) {
        showToast("Choose an event and a show first.", true);
        showSelect.focus();
        return;
    }

    loadSeatsButton.disabled = true;
    loadSeatsButton.textContent = "Loading…";
    seatArea.hidden = true;

    try {
        const seats = await apiRequest(
            `${API}/shows/${encodeURIComponent(showId)}/seats`
        );

        if (!Array.isArray(seats)) {
            throw new Error("The server returned an unexpected seat list.");
        }

        currentSeats = seats;
        renderSeats();
        seatArea.hidden = false;

        showToast(
            seats.length
                ? `${seats.length} seats loaded.`
                : "No seats were found for this show."
        );
    } catch (error) {
        showToast(`Could not load seats: ${error.message}`, true);
    } finally {
        loadSeatsButton.disabled = false;
        loadSeatsButton.innerHTML =
            'Find seats <span aria-hidden="true">↗</span>';
    }
}

async function bookSelectedSeat() {
    if (!selectedSeatId) {
        showToast("Select an available seat first.", true);
        return;
    }

    if (!ensureAuthenticated()) {
        return;
    }

    bookButton.disabled = true;
    bookButton.textContent = "Reserving…";

    let confirmationMessage = null;

    try {
        const result = await apiRequest(`${API}/bookings`, {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                showSeatId: selectedSeatId
            })
        });

        const bookingId = result.bookingId;

        if (bookingId != null) {
            bookingIdInput.value = bookingId;
            confirmationMessage =
                `Booking confirmed. Booking ID: ${bookingId}`;
        } else {
            confirmationMessage =
                result.message || "Booking completed.";
        }

        showToast(confirmationMessage);

        await loadSeats();
        await loadHistory();

        // loadSeats shows its own toast, so repeat the
        // confirmation after the seat list has reloaded.
        showToast(confirmationMessage);
    } catch (error) {
        const failureMessage = `Booking failed: ${error.message}`;

        if (error.status === 401) {
            clearAuth();
        }

        await loadSeats();

        // loadSeats shows its own toast, so repeat the
        // failure message after the seat list has reloaded.
        showToast(failureMessage, true);
    } finally {
        bookButton.innerHTML =
            'Confirm booking <span aria-hidden="true">↗</span>';
        bookButton.disabled = true;
    }
}

async function cancelBookingById(bookingId) {
    if (!ensureAuthenticated()) {
        return;
    }

    try {
        const result = await apiRequest(
            `${API}/bookings/${encodeURIComponent(bookingId)}/cancel`,
            { method: "PUT" }
        );

        const confirmationMessage =
            `Booking ${result.id ?? bookingId} cancelled successfully.`;

        showToast(confirmationMessage);

        if (!seatArea.hidden && showSelect.value) {
            await loadSeats();
        }

        await loadHistory();

        // loadSeats shows its own toast, so repeat the
        // confirmation after the seat list has reloaded.
        showToast(confirmationMessage);
    } catch (error) {
        showToast(`Cancellation failed: ${error.message}`, true);

        if (error.status === 401) {
            clearAuth();
        }
    }
}

async function cancelBooking() {
    const bookingId = bookingIdInput.value.trim();

    if (!bookingId || !Number.isInteger(Number(bookingId)) ||
        Number(bookingId) < 1) {
        showToast("Enter a valid booking ID.", true);
        bookingIdInput.focus();
        return;
    }

    cancelButton.disabled = true;
    cancelButton.textContent = "Cancelling…";

    try {
        await cancelBookingById(bookingId);
    } finally {
        cancelButton.disabled = false;
        cancelButton.textContent = "Cancel booking";
    }
}

async function loadHistory() {
    if (!authHeader) {
        historyList.innerHTML =
            '<p class="field-hint">Sign in to see your bookings here.</p>';
        return;
    }

    try {
        const bookings = await apiRequest(`${API}/bookings/my`);

        if (!Array.isArray(bookings) || bookings.length === 0) {
            historyList.innerHTML =
                '<p class="field-hint">No bookings yet. Choose a seat above to make your first booking.</p>';
            return;
        }

        historyList.replaceChildren();

        bookings.forEach((booking) => {
            const item = document.createElement("div");
            item.className = "history-item";

            const text = document.createElement("div");
            text.innerHTML =
                `<strong>Booking #${escapeHtml(booking.id)}</strong>` +
                ` · Show seat #${escapeHtml(booking.showSeatId)}` +
                ` · ${escapeHtml(booking.status)}` +
                (booking.bookedAt
                    ? ` · ${escapeHtml(new Date(booking.bookedAt).toLocaleString())}`
                    : "");
            item.appendChild(text);

            if (booking.status === "CONFIRMED") {
                const cancel = document.createElement("button");
                cancel.type = "button";
                cancel.className = "button button-secondary";
                cancel.textContent = "Cancel";
                cancel.addEventListener("click", () => {
                    bookingIdInput.value = booking.id;
                    cancelBookingById(booking.id);
                });
                item.appendChild(cancel);
            }

            historyList.appendChild(item);
        });
    } catch (error) {
        historyList.innerHTML =
            `<p class="field-hint">Could not load your bookings: ${escapeHtml(error.message)}</p>`;
    }
}

function setTheme(theme) {
    const isLight = theme === "light";

    document.body.classList.toggle("light", isLight);
    $("themeIcon").textContent = isLight ? "☾" : "☼";
    $("themeLabel").textContent = isLight ? "Dark mode" : "Light mode";
    document.documentElement.style.colorScheme =
        isLight ? "light" : "dark";

    try {
        localStorage.setItem("core-theme", theme);
    } catch {
        // Theme switching still works without browser storage.
    }
}

function initialiseTheme() {
    let theme = "dark";

    try {
        theme = localStorage.getItem("core-theme") || "dark";
    } catch {
        // Use dark mode by default.
    }

    setTheme(theme);
}

function setupNavigation() {
    document.querySelectorAll("[data-section]").forEach((button) => {
        button.addEventListener("click", () => {
            document.querySelectorAll(".nav-item[data-section]")
                .forEach((item) => item.classList.remove("active"));

            button.classList.add("active");

            const target = button.dataset.section;

            if (target === "reserve") {
                $("reserveSection").scrollIntoView({
                    behavior: "smooth"
                });
            } else if (target === "events") {
                eventSelect.focus();
                $("reserveSection").scrollIntoView({
                    behavior: "smooth"
                });
            } else if (target === "history") {
                $("historySection").scrollIntoView({
                    behavior: "smooth"
                });
            } else if (target === "manage") {
                $("manageSection").scrollIntoView({
                    behavior: "smooth"
                });
                bookingIdInput.focus({ preventScroll: true });
            }
        });
    });
}

eventSelect.addEventListener("change", () => {
    updateEventInfo();
    loadShows(eventSelect.value);
});
loadSeatsButton.addEventListener("click", loadSeats);
bookButton.addEventListener("click", bookSelectedSeat);
cancelButton.addEventListener("click", cancelBooking);
loginForm.addEventListener("submit", handleLogin);
registerForm.addEventListener("submit", handleRegister);
logoutButton.addEventListener("click", () => {
    clearAuth();
    showToast("Signed out.");
});
refreshHistoryButton.addEventListener("click", loadHistory);

$("themeToggle").addEventListener("click", () => {
    const nextTheme = document.body.classList.contains("light")
        ? "dark"
        : "light";

    setTheme(nextTheme);
});

showSelect.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
        loadSeats();
    }
});

initialiseTheme();
setupNavigation();
loadEvents();
