package org.navalplanner.web.orders;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.navalplanner.business.BusinessGlobalNames.BUSINESS_SPRING_CONFIG_FILE;
import static org.navalplanner.web.WebappGlobalNames.WEBAPP_SPRING_CONFIG_FILE;
import static org.navalplanner.web.test.WebappGlobalNames.WEBAPP_SPRING_CONFIG_TEST_FILE;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.junit.Test;
import org.junit.matchers.JUnitMatchers;
import org.junit.runner.RunWith;
import org.navalplanner.business.common.IAdHocTransactionService;
import org.navalplanner.business.common.IOnTransaction;
import org.navalplanner.business.common.exceptions.InstanceNotFoundException;
import org.navalplanner.business.common.exceptions.ValidationException;
import org.navalplanner.business.orders.daos.IOrderDAO;
import org.navalplanner.business.orders.entities.HoursGroup;
import org.navalplanner.business.orders.entities.Order;
import org.navalplanner.business.orders.entities.OrderElement;
import org.navalplanner.business.orders.entities.OrderLine;
import org.navalplanner.business.orders.entities.OrderLineGroup;
import org.navalplanner.business.planner.entities.Task;
import org.navalplanner.business.planner.entities.TaskElement;
import org.navalplanner.business.planner.entities.TaskGroup;
import org.navalplanner.business.resources.daos.ICriterionTypeDAO;
import org.navalplanner.business.resources.entities.Criterion;
import org.navalplanner.business.resources.entities.CriterionType;
import org.navalplanner.web.resources.criterion.ICriterionsModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.NotTransactional;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests for {@link OrderModel}. <br />
 * @author Óscar González Fernández <ogonzalez@igalia.com>
 * @author Manuel Rego Casasnovas <mrego@igalia.com>
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { BUSINESS_SPRING_CONFIG_FILE,
        WEBAPP_SPRING_CONFIG_FILE, WEBAPP_SPRING_CONFIG_TEST_FILE })
@Transactional
public class OrderModelTest {

    public static Date year(int year) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(Calendar.YEAR, year);
        return calendar.getTime();
    }

    private static Order createValidOrder() {
        Order order = new Order();
        order.setDescription("description");
        order.setCustomer("blabla");
        order.setInitDate(year(2000));
        order.setName("name");
        order.setResponsible("responsible");
        return order;
    }

    @Autowired
    private IOrderModel orderModel;

    @Autowired
    private IOrderDAO orderDAO;

    @Autowired
    private ICriterionTypeDAO criterionTypeDAO;

    @Autowired
    private SessionFactory sessionFactory;

    @Autowired
    private IAdHocTransactionService adHocTransaction;

    @Autowired
    private ICriterionsModel criterionModel;

    private Criterion criterion;

    private Session getSession() {
        return sessionFactory.getCurrentSession();
    }

    @Test
    public void testCreation() throws ValidationException {
        Order order = createValidOrder();
        orderModel.setOrder(order);
        orderModel.save();
        assertTrue(orderDAO.exists(order.getId()));
    }

    @Test
    public void testListing() throws Exception {
        List<Order> list = orderModel.getOrders();
        Order order = createValidOrder();
        orderModel.setOrder(order);
        orderModel.save();
        assertThat(orderModel.getOrders().size(), equalTo(list.size() + 1));
    }

    @Test
    public void testRemove() throws Exception {
        Order order = createValidOrder();
        orderModel.setOrder(order);
        orderModel.save();
        assertTrue(orderDAO.exists(order.getId()));
        orderModel.remove(order);
        assertFalse(orderDAO.exists(order.getId()));
    }

    @Test
    public void removingOrderWithAssociatedTasksDeletesThem()
            throws ValidationException, InstanceNotFoundException {
        Order order = createValidOrder();
        OrderLine orderLine = OrderLine.create();
        orderLine.setName("bla");
        orderLine.setCode("00000000");
        orderLine.setWorkHours(10);
        order.add(orderLine);
        orderModel.setOrder(order);
        orderModel.save();
        orderModel.convertToScheduleAndSave(order);
        getSession().flush();
        getSession().evict(order);
        Order reloaded = orderDAO.find(order.getId());
        OrderElement e = reloaded.getOrderElements().iterator().next();
        assertThat(e.getTaskElements().size(), equalTo(1));
        Set<TaskElement> taskElements = e.getTaskElements();
        for (TaskElement t : taskElements) {
            if (t instanceof Task) {
                Task task = (Task) t;
                task.getHoursGroup().dontPoseAsTransientObjectAnymore();
                task.getOrderElement().dontPoseAsTransientObjectAnymore();
            }
        }
        orderModel.remove(reloaded);
        orderModel.setOrder(reloaded);
        assertFalse(orderDAO.exists(order.getId()));
    }

    @Test(expected = ValidationException.class)
    public void shouldSendValidationExceptionIfEndDateIsBeforeThanStartingDate()
            throws ValidationException {
        Order order = createValidOrder();
        order.setEndDate(year(0));
        orderModel.setOrder(order);
        orderModel.save();
    }

    @Test
    public void testFind() throws Exception {
        Order order = createValidOrder();
        orderModel.setOrder(order);
        orderModel.save();
        assertThat(orderDAO.find(order.getId()), notNullValue());
    }

    @Test
    @NotTransactional
    public void testOrderPreserved() throws ValidationException,
            InstanceNotFoundException {
        final Order order = createValidOrder();
        final OrderElement[] containers = new OrderLineGroup[10];
        for (int i = 0; i < containers.length; i++) {
            containers[i] = OrderLineGroup.create();
            containers[i].setName("bla");
            containers[i].setCode("000000000");
            order.add(containers[i]);
        }
        OrderLineGroup container = (OrderLineGroup) containers[0];

        final OrderElement[] orderElements = new OrderElement[10];
        for (int i = 0; i < orderElements.length; i++) {
            OrderLine leaf = createValidLeaf("bla");
            orderElements[i] = leaf;
            container.add(leaf);
        }

        for (int i = 1; i < containers.length; i++) {
            OrderLineGroup orderLineGroup = (OrderLineGroup) containers[i];
            OrderLine leaf = createValidLeaf("foo");
            orderLineGroup.add(leaf);
        }

        orderModel.setOrder(order);
        orderModel.save();
        adHocTransaction.onTransaction(new IOnTransaction<Void>() {

            @Override
            public Void execute() {
                try {
                    Order reloaded = orderDAO.find(order.getId());
                    List<OrderElement> elements = reloaded.getOrderElements();
                    for (int i = 0; i < containers.length; i++) {
                        assertThat(elements.get(i).getId(),
                                equalTo(containers[i].getId()));
                    }
                    OrderLineGroup container = (OrderLineGroup) reloaded
                            .getOrderElements().iterator().next();
                    List<OrderElement> children = container.getChildren();
                    for (int i = 0; i < orderElements.length; i++) {
                        assertThat(children.get(i).getId(),
                                equalTo(orderElements[i].getId()));
                    }
                    for (int i = 1; i < containers.length; i++) {
                        OrderLineGroup orderLineGroup = (OrderLineGroup) containers[i];
                        assertThat(orderLineGroup.getChildren().size(),
                                equalTo(1));
                    }
                    return null;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

        });
        orderModel.remove(order);
    }

    private OrderLine createValidLeaf(String parameter) {
        OrderLine result = OrderLine.create();
        result.setName(parameter);
        result.setCode("000000000");

        HoursGroup hoursGroup = HoursGroup.create(result);
        hoursGroup.setWorkingHours(0);
        result.addHoursGroup(hoursGroup);

        return result;
    }

    @Test
    @NotTransactional
    public void testAddingOrderElement() throws Exception {
        final Order order = createValidOrder();
        OrderLineGroup container = OrderLineGroup.create();
        container.setName("bla");
        container.setCode("000000000");
        OrderLine leaf = OrderLine.create();
        leaf.setName("leaf");
        leaf.setCode("000000000");
        container.add(leaf);
        order.add(container);
        HoursGroup hoursGroup = HoursGroup.create(leaf);
        hoursGroup.setWorkingHours(3);
        leaf.addHoursGroup(hoursGroup);
        orderModel.setOrder(order);
        orderModel.save();
        adHocTransaction.onTransaction(new IOnTransaction<Void>() {

            @Override
            public Void execute() {
                try {
                    Order reloaded = orderDAO.find(order.getId());
                    assertFalse(order == reloaded);
                    assertThat(reloaded.getOrderElements().size(), equalTo(1));
                    OrderLineGroup containerReloaded = (OrderLineGroup) reloaded
                            .getOrderElements().get(0);
                    assertThat(containerReloaded.getHoursGroups().size(),
                            equalTo(1));
                    assertThat(containerReloaded.getChildren().size(),
                            equalTo(1));
                    OrderElement leaf = containerReloaded.getChildren().get(0);
                    assertThat(leaf.getHoursGroups().size(), equalTo(1));
                    orderModel.remove(order);
                } catch (InstanceNotFoundException e) {
                    throw new RuntimeException(e);
                }
                return null;
            }
        });
    }

    @Test
    @NotTransactional
    public void testManyToManyHoursGroupCriterionMapping() throws Exception {
        givenCriterion();
        final Order order = createValidOrder();

        OrderLine orderLine = OrderLine.create();
        orderLine.setName("Order element");
        orderLine.setCode("000000000");
        order.add(orderLine);

        HoursGroup hoursGroup = HoursGroup.create(orderLine);
        hoursGroup.setWorkingHours(10);
        HoursGroup hoursGroup2 = HoursGroup.create(orderLine);
        hoursGroup2.setWorkingHours(5);

        orderLine.addHoursGroup(hoursGroup);
        orderLine.addHoursGroup(hoursGroup2);

        hoursGroup.addCriterion(criterion);
        hoursGroup2.addCriterion(criterion);

        orderModel.setOrder(order);
        orderModel.save();

        adHocTransaction.onTransaction(new IOnTransaction<Void>() {

            @Override
            public Void execute() {
                try {
                    Order reloaded = orderDAO.find(order.getId());

                    List<OrderElement> orderElements = reloaded
                            .getOrderElements();
                    assertThat(orderElements.size(), equalTo(1));

                    List<HoursGroup> hoursGroups = orderElements.get(0)
                            .getHoursGroups();
                    assertThat(hoursGroups.size(), equalTo(2));

                    Set<Criterion> criterions = hoursGroups.get(0)
                            .getCriterions();
                    assertThat(criterions.size(), equalTo(1));

                    Criterion criterion = criterions.iterator().next();

                    assertThat(criterion.getName(),
                            equalTo(OrderModelTest.this.criterion.getName()));
                } catch (InstanceNotFoundException e) {
                    throw new RuntimeException(e);
                }
                return null;
            }
        });

    }

    private void givenCriterion() throws ValidationException {
        this.criterion = adHocTransaction
                .onTransaction(new IOnTransaction<Criterion>() {

                    @Override
                    public Criterion execute() {
                        // TODO Auto-generated method stub
                        CriterionType criterionType = new CriterionType("test"
                                + UUID.randomUUID());
                        criterionTypeDAO.save(criterionType);
                        Criterion criterion = new Criterion("Test"
                                + UUID.randomUUID(), criterionType);
                        try {
                            criterionModel.save(criterion);
                        } catch (ValidationException e) {
                            throw new RuntimeException(e);
                        }
                        return criterion;
                    }
                });
    }

    @Test(expected = ValidationException.class)
    public void testAtLeastOneHoursGroup() throws Exception {
        Order order = createValidOrder();

        OrderLine orderLine = OrderLine.create();
        orderLine.setName("foo");
        orderLine.setCode("000000000");
        order.add(orderLine);

        orderModel.setOrder(order);
        orderModel.save();
    }

    @Test
    public void aOrderLineGroupIsConvertedToATaskGroup() {
        OrderLineGroup orderLineGroup = OrderLineGroup.create();
        orderLineGroup.setName("foo");
        orderLineGroup.setCode("000000000");
        TaskElement task = orderModel.convertToInitialSchedule(orderLineGroup);
        assertThat(task, is(TaskGroup.class));

        TaskGroup group = (TaskGroup) task;
        assertThat(group.getOrderElement(),
                equalTo((OrderElement) orderLineGroup));
    }

    @Test
    public void aOrderLineWithOneHourGroupIsConvertedToATask() {
        OrderLine orderLine = OrderLine.create();
        orderLine.setName("bla");
        orderLine.setCode("000000000");
        final int hours = 30;
        HoursGroup hoursGroup = createHoursGroup(hours);
        orderLine.addHoursGroup(hoursGroup);
        TaskElement taskElement = orderModel
                .convertToInitialSchedule(orderLine);
        assertThat(taskElement, is(Task.class));

        Task group = (Task) taskElement;
        assertThat(group.getOrderElement(), equalTo((OrderElement) orderLine));
        assertThat(group.getHoursGroup(), equalTo(hoursGroup));
        assertThat(taskElement.getWorkHours(), equalTo(hours));
    }

    @Test
    public void theSublinesOfAnOrderLineGroupAreConverted() {
        OrderLineGroup orderLineGroup = OrderLineGroup.create();
        orderLineGroup.setName("foo");
        orderLineGroup.setCode("000000000");
        OrderLine orderLine = OrderLine.create();
        orderLine.setName("bla");
        orderLine.setCode("000000000");
        HoursGroup hoursGroup = createHoursGroup(30);
        orderLine.addHoursGroup(hoursGroup);
        orderLineGroup.add(orderLine);
        TaskElement task = orderModel.convertToInitialSchedule(orderLineGroup);
        assertThat(task, is(TaskGroup.class));

        TaskGroup group = (TaskGroup) task;

        assertThat(group.getOrderElement(),
                equalTo((OrderElement) orderLineGroup));
        assertThat(group.getChildren().size(), equalTo(1));
        assertThat(group.getChildren().get(0).getOrderElement(),
                equalTo((OrderElement) orderLine));
    }

    @Test
    public void theWorkHoursOfATaskGroupAreTheSameThanTheTaskElement() {
        OrderLineGroup orderLineGroup = OrderLineGroup.create();
        orderLineGroup.setName("foo");
        orderLineGroup.setCode("000000000");
        OrderLine orderLine = OrderLine.create();
        orderLine.setName("bla");
        orderLine.setCode("000000000");
        orderLine.addHoursGroup(createHoursGroup(20));
        orderLine.addHoursGroup(createHoursGroup(30));
        orderLineGroup.add(orderLine);
        TaskElement task = orderModel.convertToInitialSchedule(orderLineGroup);
        assertThat(task.getWorkHours(), equalTo(orderLineGroup.getWorkHours()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void aOrderLineWithNoHoursIsRejected() {
        OrderLine orderLine = OrderLine.create();
        orderLine.setName("bla");
        orderLine.setCode("000000000");
        orderModel.convertToInitialSchedule(orderLine);
    }

    private HoursGroup createHoursGroup(int hours) {
        HoursGroup result = new HoursGroup();
        result.setWorkingHours(hours);
        return result;
    }

    @Test
    public void aOrderLineWithMoreThanOneHourIsConvertedToATaskGroup() {
        OrderLine orderLine = OrderLine.create();
        orderLine.setName("bla");
        orderLine.setCode("000000000");
        HoursGroup hours1 = createHoursGroup(30);
        orderLine.addHoursGroup(hours1);
        HoursGroup hours2 = createHoursGroup(10);
        orderLine.addHoursGroup(hours2);
        TaskElement taskElement = orderModel
                .convertToInitialSchedule(orderLine);
        assertThat(taskElement, is(TaskGroup.class));

        TaskGroup group = (TaskGroup) taskElement;
        assertThat(group.getOrderElement(), equalTo((OrderElement) orderLine));
        assertThat(group.getChildren().size(), equalTo(2));

        Task child1 = (Task) group.getChildren().get(0);
        Task child2 = (Task) group.getChildren().get(1);

        assertThat(child1.getOrderElement(), equalTo((OrderElement) orderLine));
        assertThat(child2.getOrderElement(), equalTo((OrderElement) orderLine));

        assertThat(child1.getHoursGroup(), not(equalTo(child2.getHoursGroup())));

        assertThat(child1.getHoursGroup(), JUnitMatchers
                .either(equalTo(hours1)).or(equalTo(hours2)));
        assertThat(child2.getHoursGroup(), JUnitMatchers
                .either(equalTo(hours1)).or(equalTo(hours2)));
    }

}
