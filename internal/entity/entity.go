// Package entity berisi struct yang dipetakan ke koleksi MongoDB.
// Tiap struct = satu koleksi, dengan tag bson yang menyamai pola dokumen
// dari versi Spring Data (camelCase, kecuali _id untuk primary key).
package entity

import "time"

type Role string

const (
	RoleAO    Role = "AO"
	RolePIMP  Role = "PIMP"
	RoleAdmin Role = "ADMIN"
)

type User struct {
	ChatID   int64  `bson:"_id"`
	UserCode string `bson:"userCode,omitempty"`
	Branch   string `bson:"branch,omitempty"`
	Roles    Role   `bson:"roles,omitempty"`
}

type Bills struct {
	NoSpk             string `bson:"_id"`
	CustomerID        string `bson:"customerId,omitempty"`
	Wilayah           string `bson:"wilayah,omitempty"`
	Branch            string `bson:"branch,omitempty"`
	OfficeLocation    string `bson:"officeLocation,omitempty"`
	Product           string `bson:"product,omitempty"`
	Name              string `bson:"name,omitempty"`
	Address           string `bson:"address,omitempty"`
	PayDown           string `bson:"payDown,omitempty"`
	Realization       string `bson:"realization,omitempty"`
	DueDate           string `bson:"dueDate,omitempty"`
	CollectStatus     string `bson:"collectStatus,omitempty"`
	DayLate           string `bson:"dayLate,omitempty"`
	Plafond           int64  `bson:"plafond,omitempty"`
	DebitTray         int64  `bson:"debitTray,omitempty"`
	Interest          int64  `bson:"interest,omitempty"`
	Principal         int64  `bson:"principal,omitempty"`
	Installment       int64  `bson:"installment,omitempty"`
	LastInterest      int64  `bson:"lastInterest,omitempty"`
	LastPrincipal     int64  `bson:"lastPrincipal,omitempty"`
	LastInstallment   int64  `bson:"lastInstallment,omitempty"`
	FullPayment       int64  `bson:"fullPayment,omitempty"`
	MinInterest       int64  `bson:"minInterest,omitempty"`
	MinPrincipal      int64  `bson:"minPrincipal,omitempty"`
	PenaltyInterest   int64  `bson:"penaltyInterest,omitempty"`
	PenaltyPrincipal  int64  `bson:"penaltyPrincipal,omitempty"`
	AccountOfficer    string `bson:"accountOfficer,omitempty"`
	Kios              string `bson:"kios,omitempty"`
	Titipan           int64  `bson:"titipan,omitempty"`
	FixedInterest     int64  `bson:"fixedInterest,omitempty"`
	CKPNType          string `bson:"ckpnType,omitempty"`
	CKPNNominal       int64  `bson:"ckpnNominal,omitempty"`
	RekeningAutobedet string `bson:"rekeningAutobedet,omitempty"`
}

type Savings struct {
	ID              string `bson:"_id,omitempty" json:"id,omitempty"`
	Branch          string `bson:"branch,omitempty" json:"branch,omitempty"`
	Type            string `bson:"type,omitempty" json:"type,omitempty"`
	CIF             string `bson:"cif,omitempty" json:"cif,omitempty"`
	TabID           string `bson:"tabId,omitempty" json:"tabId,omitempty"`
	Name            string `bson:"name,omitempty" json:"name,omitempty"`
	Address         string `bson:"address,omitempty" json:"address,omitempty"`
	Balance         int64  `bson:"balance,omitempty" json:"balance,omitempty"`
	Transaction     int64  `bson:"transaction,omitempty" json:"transaction,omitempty"`
	AccountOfficer  string `bson:"accountOfficer,omitempty" json:"accountOfficer,omitempty"`
	Phone           string `bson:"phone,omitempty" json:"phone,omitempty"`
	MinimumBalance  int64  `bson:"minimumBalance,omitempty" json:"minimumBalance,omitempty"`
	BlockingBalance int64  `bson:"blockingBalance,omitempty" json:"blockingBalance,omitempty"`
}

type KolekTas struct {
	ID             string `bson:"_id,omitempty"`
	Kelompok       string `bson:"kelompok,omitempty"`
	Kantor         string `bson:"kantor,omitempty"`
	Rekening       string `bson:"rekening,omitempty"`
	Nama           string `bson:"nama,omitempty"`
	Alamat         string `bson:"alamat,omitempty"`
	NoHP           string `bson:"noHp,omitempty"`
	Kolek          string `bson:"kolek,omitempty"`
	Nominal        string `bson:"nominal,omitempty"`
	AccountOfficer string `bson:"accountOfficer,omitempty"`
	CIF            string `bson:"cif,omitempty"`
}

type Paying struct {
	ID   string `bson:"_id"`
	Paid bool   `bson:"paid"`
}

type Payment struct {
	ID     string `bson:"_id,omitempty"`
	Amount int64  `bson:"amount"`
	User   string `bson:"user,omitempty"`
	IsPaid bool   `bson:"isPaid"`
}

type PaymentDetails struct {
	ID              string `bson:"_id"`
	Tanggal         string `bson:"tanggal,omitempty"`
	KodeCabang      string `bson:"kodeCabang,omitempty"`
	KodeAO          string `bson:"kodeAo,omitempty"`
	NoSpk           string `bson:"noSpk,omitempty"`
	Nama            string `bson:"nama,omitempty"`
	KodePosting     string `bson:"kodePosting,omitempty"`
	NominalAngsuran int64  `bson:"nominalAngsuran,omitempty"`
	Denda           int64  `bson:"denda,omitempty"`
	Penalti         int64  `bson:"penalti,omitempty"`
	FlagPelunasan   bool   `bson:"flagPelunasan,omitempty"`
}

type CreditHistory struct {
	ID         string `bson:"_id,omitempty"`
	Date       int64  `bson:"date,omitempty"`
	CreditID   string `bson:"creditId,omitempty"`
	CustomerID string `bson:"customerId,omitempty"`
	Name       string `bson:"name,omitempty"`
	Status     string `bson:"status,omitempty"`
	Address    string `bson:"address,omitempty"`
	Phone      string `bson:"phone,omitempty"`
}

type DataUpdateLog struct {
	DataType  string    `bson:"_id"`
	UpdatedAt time.Time `bson:"updatedAt"`
}

type SlikNotifiedFile struct {
	FileKey    string    `bson:"_id"`
	NotifiedAt time.Time `bson:"notifiedAt"`
}

const MinBungaSessionTTL = 30 * time.Minute

type MinBungaSession struct {
	ChatID        string    `bson:"_id"`
	Identifier    string    `bson:"identifier,omitempty"`
	Role          string    `bson:"role,omitempty"`
	SelectedDates []string  `bson:"selectedDates,omitempty"`
	MessageID     int64     `bson:"messageId,omitempty"`
	CreatedAt     time.Time `bson:"createdAt"`
}
