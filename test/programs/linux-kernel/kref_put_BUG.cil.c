/* Generated by CIL v. 1.3.7 */
/* print_CIL_Input is true */

#line 3 "kref_put.c"
struct kref {
   int refcount ;
};
#line 7 "kref_put.c"
struct usb_serial {
   struct kref kref ;
};
#line 71 "/usr/include/assert.h"
extern  __attribute__((__nothrow__, __noreturn__)) void __assert_fail(char const   *__assertion ,
                                                                      char const   *__file ,
                                                                      unsigned int __line ,
                                                                      char const   *__function ) ;
#line 11 "kref_put.c"
extern int atomic_dec_and_test(int *cnt ) ;
#line 13 "kref_put.c"
int kref_put(struct kref *kref , void (*release)(struct kref *kref ) ) 
{ int tmp ;
  int *__cil_tmp4 ;

  {
  {
#line 15
  __cil_tmp4 = (int *)kref;
#line 15
  tmp = atomic_dec_and_test(__cil_tmp4);
  }
#line 15
  if (tmp) {
    {
#line 16
    (*release)(kref);
    }
#line 17
    return (1);
  } else {

  }
#line 19
  return (0);
}
}
#line 23 "kref_put.c"
int ldv_lock  =    0;
#line 25 "kref_put.c"
static void destroy_serial(struct kref *kref ) 
{ 

  {
#line 27
  if (ldv_lock == 0) {

  } else {
    {
#line 27
    __assert_fail("ldv_lock==0", "kref_put.c", 27U, "destroy_serial");
    }
  }
#line 28
  return;
}
}
#line 30 "kref_put.c"
int table_lock  ;
#line 32 "kref_put.c"
void spin_lock(int *lock ) 
{ 

  {
#line 33
  ldv_lock = 1;
#line 34
  return;
}
}
#line 36 "kref_put.c"
void spin_unlock(int *lock ) 
{ 

  {
#line 37
  ldv_lock = 0;
#line 38
  return;
}
}
#line 40 "kref_put.c"
void usb_serial_put(struct usb_serial *serial ) 
{ struct kref *__cil_tmp2 ;

  {
  {
#line 42
  spin_lock(& table_lock);
#line 43
  __cil_tmp2 = (struct kref *)serial;
#line 43
  kref_put(__cil_tmp2, & destroy_serial);
#line 44
  spin_unlock(& table_lock);
  }
#line 45
  return;
}
}
#line 47 "kref_put.c"
int main(void) 
{ struct usb_serial serial ;

  {
  {
#line 50
  usb_serial_put(& serial);
  }
#line 51
  return (0);
}
}
