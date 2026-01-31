package users;

public class Admin extends User{
    int adminID;
    public Admin(int id, String name, String email, String password, int adminID) {
        super(id, name, email, password);
        this.adminID = adminID;
    }
}
